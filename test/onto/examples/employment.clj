(ns onto.examples.employment
  (:require [onto.core :as o]))

(def employment-properties
  (o/properties
   (o/oproperties :isEmployedBy :worksFor :contractsTo)
   (o/subproperty :isEmployedBy          :worksFor)
   (o/subproperty :contractsTo           :worksFor)))

(def add-contractor-relationship
  (o/properties
   (o/oproperties :freelancesTo :indirectlyContractsTo)
   (o/subproperty :freelancesTo          :contractsTo)
   (o/subproperty :indirectlyContractsTo :contractsTo)))

(def workers-in-firm
  (o/nodes
   (o/t "Goldman" :isEmployedBy          "TheFirm")
   (o/t "McAdams" :worksFor              "TheFirm")))

(def add-contractors
  (o/nodes
   (o/t "Spence"  :freelancesTo          "TheFirm")
   (o/t "Long"    :indirectlyContractsTo "TheFirm")))
