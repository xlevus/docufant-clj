(ns docufant.operator
  (:require [docufant.honeysql :refer [jsonb-path]]))

(defn =
  ([value] ["jsonb=" (jsonb-path :_data) value])
  ([path value] ["jsonb=" (jsonb-path :_data path) value]))
