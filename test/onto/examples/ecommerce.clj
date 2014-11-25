(ns onto.examples.ecommerce
  (:require [onto.core :as o]
            [onto.examples.helpers :refer :all]
            [onto.traits :as t]))

(defn shipping-methods   [e]    (into #{} (map :carrier (:shipping-methods (.e e)))))
(defn digital-delivery   [& _]  #{"email" "dropbox"})
(defn harvesting-methods [e]    (into #{} (-> e .e :harvesting-methods)))

(def product-catalog
  (o/properties
   (o/dtproperty :short-description :string :one)
   (o/domain     :short-description "SKU")

   (o/dtproperty :street-date :instant :one)
   (o/domain     :street-date "SKU")

   (o/dtproperty :long-description :string :one)))

(def shipping
  (o/properties
   (o/oproperty :shipping-methods :many)
   (o/range     :shipping-methods "ShippingMethod")
   (o/domain    :shipping-methods "Shippable")
   (o/dtproperty :carrier :string :one)

   (o/dtproperty :tracking-number :string :one)
   (o/oproperty  :shipment :many)
   (o/range      :shipment "Shipment")))

(def harvesting
  (o/properties
   (o/dtproperty :harvesting-methods :string :many)
   (o/domain     :harvesting-methods "Harvested")))

(defmacro t? [e p t] `(if ~t (o/t ~e ~p ~t)))
(defmacro v? [e p v] `(if ~v (o/v ~e ~p ~v)))

(defmacro sku [id] `(str "sku:" ~id))

(defn harvested
  [id method]
  (o/nodes
   (v? (sku id) :harvesting-methods method)))

(defn ship-by
  [id carrier]
  (o/nodes
   (v? (sku id) :shipping-methods [:carrier carrier])))

(defn make-sku
  ([id sd ld]
     (make-sku id sd ld nil))
  ([id sd ld avail]
     (let [uri (sku id)]
       (o/nodes
        (v? uri :short-description sd)
        (v? uri :long-description ld)
        (v? uri :street-date avail)))))

(t/deftrait Delivery
  (methods "Shipping methods allowed for an item."))

(t/deftrait Organic
  (methods "Harvesting methods used to collect the item."))

(def restbucks
  (o/nodes
   (make-sku "1"   "Small Latte"       "6 oz. latte with steamed milk")
   (make-sku "2"   "Medium Latte"      "6 oz. latte with steamed milk")
   (make-sku "3"   "Large Latte"       "6 oz. latte with steamed milk")
   (make-sku "100" "Obsidian beans"    "16 oz. of dark roasted coffee beans")
   (make-sku "101" "Obsidian grounds"  "16 oz. of dark roasted coffee, ground for you")
   (make-sku "200" "$10 gift card"     "$10 gift card")
   (make-sku "201" "$10 digital bucks" "Digital bucks")

   (harvested "100" "hand picked (human)")
   (harvested "100" "hand picked (trained monkey)")
   (harvested "100" "machine collected")

   (ship-by "200" "USPS")
   (ship-by "200" "UPS")
   (ship-by "101" "USPS")

   (o/type (sku "201") "DigitalGood")

   (t/extend-trait Delivery "Shippable"
                   (methods onto.examples.ecommerce/shipping-methods))

   (t/extend-trait Delivery "DigitalGood"
                   (methods onto.examples.ecommerce/digital-delivery))

   (t/extend-trait Organic "Harvested"
                   (methods onto.examples.ecommerce/harvesting-methods))))
