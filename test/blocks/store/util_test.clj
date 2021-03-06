(ns blocks.store.util-test
  (:require
    [blocks.core :as block]
    [blocks.store.util :as util]
    [clojure.test :refer :all]
    [multihash.core :as multihash]))


(deftest check-macro
  (testing "check with true predicate"
    (let [effects (atom [])]
      (is (= :foo (util/check :foo some?
                    (swap! effects conj [:> value])))
          "should return value")
      (is (empty? @effects) "should not cause side effects")))
  (testing "check with false predicate"
    (let [effects (atom [])]
      (is (nil? (util/check :foo (constantly false)
                  (swap! effects conj [:> value])))
          "should return nil")
      (is (= [[:> :foo]] @effects) "should cause side effects"))))


(deftest block-preference
  (is (nil? (util/preferred-copy nil))
      "returns nil with no block arguments")
  (let [literal (block/read! "foo")
        lazy-a (block/from-file "project.clj")
        lazy-b (block/from-file "README.md")]
    (is (= literal (util/preferred-copy lazy-a literal lazy-b))
        "returns literal block if present")
    (is (= lazy-a (util/preferred-copy lazy-a lazy-b))
        "returns first block if all lazy")))


(deftest stat-selection
  (let [a (multihash/create :sha1 "37b51d194a7513e45b56f6524f2d51f200000000")
        b (multihash/create :sha1 "73fcffa4b7f6bb68e44cf984c85f6e888843d7f9")
        c (multihash/create :sha1 "73fe285cedef654fccc4a4d818db4cc225932878")
        d (multihash/create :sha1 "acbd18db4cc2f856211de9ecedef654fccc4a4d8")
        e (multihash/create :sha1 "c3c23db5285662ef717963ff4ce2373df0003206")
        f (multihash/create :sha2-256 "285c3c23d662b5ef7172373df0963ff4ce003206")
        ids [a b c d e f]
        stats (map #(hash-map :id % :size 1) ids)]
    (are [result opts] (= result (map :id (util/select-stats opts stats)))
         ids        {}
         [f]        {:algorithm :sha2-256}
         [c d e f]  {:after "111473fd2"}
         [a b c]    {:limit 3})))


(deftest stat-list-merging
  (let [list-a (list {:id "aaa", :foo :bar}
                     {:id "abb", :baz :qux}
                     {:id "abc", :key :val})
        list-b (list {:id "aab", :xyz 123}
                     {:id "abc", :ack :bar})
        list-c (list {:id "aaa", :foo 123}
                     {:id "xyz", :wqr :axo})]
    (is (= [{:id "aaa", :foo :bar}
            {:id "aab", :xyz 123}
            {:id "abb", :baz :qux}
            {:id "abc", :key :val}
            {:id "xyz", :wqr :axo}]
           (util/merge-block-lists
             list-a list-b list-c)))))


(deftest uri-parsing
  (is (= {:scheme "mem", :name "-"} (util/parse-uri "mem:-")))
  (is (= {:scheme "file", :path "/foo/bar"} (util/parse-uri "file:///foo/bar")))
  (is (= {:scheme "file", :host "foo" :path "/bar"} (util/parse-uri "file://foo/bar")))
  (is (= {:scheme "https"
          :user-info {:id "user"
                      :secret "password"}
          :host "example.com"
          :port 443
          :path "/path/to/thing"
          :query {:foo "alpha"
                  :bar "123"}}
         (util/parse-uri "https://user:password@example.com:443/path/to/thing?foo=alpha&bar=123"))))
