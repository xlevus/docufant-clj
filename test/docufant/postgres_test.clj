(ns docufant.postgres-test
  (:require [docufant.postgres :as pg]
            [cheshire.core :refer [generate-string]]
            [clojure.test :as t :refer [is deftest]])
  (:import org.postgresql.util.PGobject))


(defn is-pgobject [x]
  (is (= PGobject
         (type x)))
  x)


(defn is-pgobject-type [x type]
  (is (= type (.getType x)))
  x)


(defn is-pgobject-value [x value]
  (is (= value (.getValue x)))
  x)


(defn is-json-value [x value]
  (is-pgobject-value x (generate-string value)))


(deftest test-jsonb
  (-> (pg/jsonb {:a 1})
      (is-pgobject)
      (is-pgobject-type "jsonb")
      (is-json-value {"a" 1}))

  (-> (pg/jsonb 1)
      (is-pgobject)
      (is-pgobject-type "jsonb")
      (is-json-value 1))

  (-> (pg/jsonb [1 2 3])
      (is-pgobject)
      (is-pgobject-type "jsonb")
      (is-json-value [1 2 3])))


(deftest test-text-array
  (-> (pg/text-array [1 2 3])
      (is-pgobject)
      (is-pgobject-type "text[]")
      (is-pgobject-value "{1,2,3}")))
