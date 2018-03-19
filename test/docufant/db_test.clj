(ns docufant.db-test
  (:require [docufant.db :as db]
            [clojure.test :as t :refer [deftest testing is]]))


(def ^:dynamic *db-spec* {})


(deftest test-indexname
  (is (= "dfidx_docufant_a_b"
         (db/indexname {} {:path [:a :b] })))

  (is (= "dfidx_docufant_a_b__gin"
         (db/indexname {} {:path [:a :b]
                           :index-type :gin})))

  (is (= "dfidx_docufant_a_b__uniq"
         (db/indexname {} {:path [:a :b]
                           :unique true})))

  (is (= "dfidx_foo_a_b"
         (db/indexname {:tablename "foo" :db-spec {}} {:path [:a :b] })))

  )
