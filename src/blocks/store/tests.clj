(ns blocks.store.tests
  "Suite of tests to verify that a given block store implementation conforms to
  the spec."
  (:require
    [blocks.core :as block]
    [blocks.util :as util]
    [byte-streams :as bytes]
    [clojure.test :refer :all]
    [multihash.core :as multihash])
  (:import
    blocks.data.PersistentBytes))


(defn populate-blocks!
  "Stores some test blocks in the given block store and returns a map of the
  ids to the original content values."
  [store n max-size]
  (->> (repeatedly #(util/random-bytes max-size))
       (take n)
       (map (juxt (comp :id (partial block/store! store)) identity))
       (into (sorted-map))))


(defn test-put-attributes
  "The put! method in a store should return a block with an updated content or
  reader, but keep the same id, extra attributes, and any non-stat metadata."
  [store]
  (let [original (-> (block/read! (util/random-bytes 512))
                     (assoc :foo "bar")
                     (vary-meta assoc ::thing :baz))
        stored (block/put! store original)]
    (is (= (:id original) (:id stored))
        "Stored block id should match original")
    (is (= (:size original) (:size stored))
        "Stored block size should match original")
    (is (= "bar" (:foo stored))
        "Stored block should retain extra attributes")
    (is (= :baz (::thing (meta stored)))
        "Stored block should retain extra metadata")
    (is (= original stored)
        "Stored block should test equal to original")
    (is (true? (block/delete! store (:id stored))))))


(defn test-block
  "Determines whether the store contains the content for the given identifier."
  [store id content]
  (testing "block stats"
    (let [status (block/stat store id)]
      (is (= id (:id status))
          "should return the same multihash id")
      (is (= (count content) (:size status))
          "should return the content size")))
  (testing "block retrieval"
    (let [block (block/get store id)]
      (is (= id (:id block))
          "stored block has same id")
      (is (= (count content) (:size block))
          "block contains size info")
      (with-open [stream (block/open block)]
        (is (bytes/bytes= content (bytes/to-byte-array stream))
            "stored content should match"))
      (is (= [:id :size] (keys block))
          "block only contains id and size"))))


(defn test-restore-block
  "Tests re-storing an existing block."
  [store id content]
  (let [status     (block/stat store id)
        new-block  (block/store! store content)
        new-status (block/stat store id)]
    (is (= id (:id new-block)))
    (is (= (:stored-at status)
           (:stored-at new-status)))))


(defn test-list-stats
  "Tests the functionality of list's marker option."
  [store ids n]
  (let [prefix (-> (block/list store :limit 1) first :id multihash/hex (subs 0 4))]
    (dotimes [i n]
      (let [after (str prefix (util/random-hex 6))
            limit (inc (rand-int 100))
            stats (block/list store :after after :limit limit)
            expected (->> ids
                          (filter #(pos? (compare (multihash/hex %) after)))
                          (sort)
                          (take limit))]
        (is (= expected (map :id stats))
            (str "list should return the expected ids in sorted order for: "
                 (pr-str {:after after, :limit limit})))))))


(defn test-block-store
  "Tests a block store implementation."
  [label store & {:keys [blocks max-size eraser]
                  :or {blocks 10, max-size 1024}}]
  (when-not (empty? (block/list store))
    (throw (IllegalStateException.
             (str "Cannot run integration test on " (pr-str store)
                  " as it already contains blocks!"))))
  (println "  *" label)
  (testing (.getSimpleName (class store))
    (testing "querying non-existent block"
      (is (nil? (block/stat store (multihash/sha1 "foo"))))
      (is (nil? (block/get store (multihash/sha1 "bar")))))
    (testing "put attributes"
      (test-put-attributes store))
    (let [stored-content (populate-blocks! store blocks max-size)]
      (testing "list stats"
        (let [stats (block/list store)]
          (is (= (keys stored-content) (map :id stats))
              "enumerates all ids in sorted order")
          (is (every? #(= (:size %) (count (get stored-content (:id %)))) stats)
              "returns correct size for all blocks"))
        (test-list-stats store (keys stored-content) 10))
      (doseq [[id content] stored-content]
        (test-block store id content))
      (let [[id content] (first (seq stored-content))]
        (test-restore-block store id content))
      (if eraser
        (eraser store)
        (doseq [id (keys stored-content)]
          (is (true? (block/delete! store id)))))
      (is (empty? (block/list store)) "ends empty"))))