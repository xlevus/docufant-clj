(ns docufant.db-test
  (:require [docufant.db :as db]
            [clojure.test :as t :refer [deftest testing is]]))


(def ^:dynamic *db-spec* [])


(deftest test-indexes
  (testing "format-index"
    (is (= ["CREATE INDEX IF NOT EXISTS idx_docufant__name ON docufant clause"]
           (db/format-index *db-spec* "name" "clause" {})))

    (is (= ["CREATE INDEX IF NOT EXISTS idx_docufant__test__typed ON docufant clause WHERE _type = ?"
            :test]
           (db/format-index *db-spec* "typed" "clause" {:type :test})))

    (is (= ["CREATE UNIQUE INDEX IF NOT EXISTS idx_docufant__unique ON docufant clause"]
           (db/format-index *db-spec* "unique" "clause" {:unique true})))

    (is (= ["CREATE INDEX idx_docufant__force ON docufant clause"]
           (db/format-index {:force true
                             :db-spec {}} "force" "clause" {})))

    )


  (testing "build-index-gin"
    (is (= ["CREATE INDEX IF NOT EXISTS idx_docufant__gin ON docufant USING GIN(_data jsonb_path_ops)"]
           (db/build-index {} {:index-type :gin
                               :gin-type :jsonb_path_ops}))))


  (testing "build-index"
    (is (= "CREATE INDEX IF NOT EXISTS idx_docufant__intdoc__a_b_c ON docufant (((_data #>> ?)::int)) WHERE _type = ?"
           (first (db/build-index {} {:path [:a :b :c]
                                      :unique true
                                      :type :intdoc
                                      :as Integer})))))
  )
