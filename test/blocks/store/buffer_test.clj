(ns blocks.store.buffer-test
  (:require
    [blocks.core :as block]
    (blocks.store
      [memory :refer [memory-block-store]]
      [buffer :refer [buffer-block-store]]
      [tests :as tests])
    [clojure.test :refer :all]))


(deftest buffer-behavior
  (let [backer (memory-block-store)
        store (buffer-block-store
                :store backer
                :buffer (memory-block-store))
        buffer (:buffer store)
        a (block/read! "foo bar baz")
        b (block/read! "abracadabra")
        c (block/read! "123 xyz")]
    (block/put! store a)
    (block/put! store b)
    (is (empty? (block/list backer)))
    (block/put! backer c)
    (is (= 3 (count (block/list store))))
    (is (= (every? (set (map :id [a b c])) (block/list store))))
    (is (= (:id c) (:id (block/put! store c))))
    (is (= 2 (count (block/list buffer))))
    (blocks.store.buffer/flush! store)
    (is (empty? (block/list buffer)))
    (is (= 3 (count (block/list backer))))
    (block/delete! store (:id c))
    (is (= 2 (count (block/list store))))))


(deftest ^:integration test-buffer-store
  (tests/check-store! #(buffer-block-store
                         :store (memory-block-store)
                         :buffer (memory-block-store))))
