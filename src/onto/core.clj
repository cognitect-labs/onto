(ns onto.core
  "Ontological inferences. Supports a subset of RDFs and OWL.

   The vars `core-schemata` and `trait-schema` must be transacted
   into your schema before you can use this. Each contains a
   sequence of transactions. Because the schemata define new
   attributes and use them, you cannot transact the schema all at
   once.

   The `bootstrap` function is a convenience function to load the
   schema.

   # Entities and Classes

   The core premise is that entities can be found to belong to one
   or more classes. This is different than OO design, where you
   proscribe class membership at creation time. Here an entity may
   gain or lose classes by virtue of properties and values that are
   attached to it.

   A Class is also an entity, therefore it is possible to create
   classes of classes.

   An entity is found to be part of a class when:

   * It is directly stated as such, via the `type` function.
   * It is a member of a subclass of the class
   * It is the target of a property whose range is the class.
   * It is the owner of a property whose domain is the class.

   These rules all hold for subproperties of the property in
   question as well.

   # Properties

   Properties are represented as Datomic attributes on entities. An
   `oproperty` (object property) resolves to another entity. A
   `dtproperty` (data property) resolves to a value. Both kinds of
   property may be single- or multi-valued.

   # Triples

   Object-valued facts can be added by declaring triples with the
   function `t`. A triple consists of a subject, property, and
   entity.

   # Transacting

   All the declarations return datoms. Properties and classes are
   instantiated the first time they are mentioned. The function
   `properties` helps weed out duplication in datoms from multiple
   declarations. `nodes` does a similar job for datoms created by
   `v` and `t`.

   # Example

   This is distilled from onto.examples.ecommerce.

       (properties
         ;; A short description is a string
         (dtproperty :short-description :string :one)

         ;; Any entity that has a short description is a SKU
         (domain :short-description \"SKU\")

         ;; A street-date is a point in time
         (dtproperty :street-date :instant :one)

         ;; Any entity that has a street date is a SKU
         (domain :street-date \"SKU\")

         ;; A long description is a string
         (dtproperty :long-description :string :one))

         ;; Create an entity with values that make it a SKU
         (defn make-sku
           ([id sd ld]
             (make-sku id sd ld nil))
           ([id sd ld avail]
             (let [uri (sku id)]
               (nodes
                 (v uri :short-description sd)
                 (v uri :long-description ld)
                 (v uri :street-date avail)))))

         ;; Ask if the entity with label \"sku:1234\" is in fact a SKU
         (has-class? \"sku:1234\" \"SKU\")

         ;; Ask if that entity is sellable
         (has-class? \"sku:1234\" \"Available\")"
  (:refer-clojure :exclude [type range])
  (:require [clojure.set :refer [intersection]]
            [datomic.api :as d]))

(def core-schemata
  [[{:db/id                 #db/id[:db.part/db]
     :db/ident              :onto
     :db.install/_partition :db.part/db}]
   [{:db/id                 #db/id[:db.part/db]
     :db/ident              :uri
     :db/valueType          :db.type/string
     :db/cardinality        :db.cardinality/one
     :db/unique             :db.unique/identity
     :db/doc                "Canonical name for an entity."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :type
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/many
     :db/doc                "Refers to a class, indicates the referring entity is a member of that class."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :subclass
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/many
     :db/doc                "Refers to a class, indicates the referring entity is a subclass of that class. By implication, the referring entity is itself a class."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :subproperty
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/many
     :db/doc                "Refers to a property, indicates the referring entity is a subproperty of that property. By implication, the referring entity is itself a property."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:onto]
     :db/ident              :Class
     :db/doc                "The type of all classes."}
    {:db/id                 #db/id[:onto]
     :db/ident              :Property
     :db/doc                "The type of all properties."}
    {:db/id                 #db/id[:onto]
     :db/ident              :FunctionalProperty
     :db/doc                "A property which implies any two referents are the same entity."}
    {:db/id                 #db/id[:onto]
     :db/ident              :InverseFunctionalProperty
     :db/doc                "A property which implies any two referrers to the same entity can be regarded as the same as each other."}
    {:db/id                 #db/id[:onto]
     :db/ident              :SomeValuesRestriction
     :db/doc                "The base type of all 'some values' restriction classes."}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :domain
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/many
     :db/doc                "Refers to a class, implies that any entity holding this property is a member of that class."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :range
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/many
     :db/doc                "Refers to a class, implies that any entity refered to by this property is a member of that class."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :on-property
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/one
     :db/doc                "Refers to a property. Part of a restriction class definition. Identifies which property of the candidate entity is to be considered."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :some-values-from
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/one
     :db/doc                "Refers to a class. Part of a restriction class definition. Identifies which class's values will nominate the canidate (i.e., the referring entity) as a member of the restriction class. Requires that at least one value of the candidate's property exists and inhabits the referenced class."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :label
     :db/valueType          :db.type/string
     :db/cardinality        :db.cardinality/one
     :db/doc                "An appellation for an entity."
     :db.install/_attribute :db.part/db}
    {:db/id                 #db/id[:db.part/db]
     :db/ident              :inverseof
     :db/valueType          :db.type/ref
     :db/cardinality        :db.cardinality/one
     :db/doc                "Refers to a property P. Indicates that this property Q has the inference x P y -> y Q x."
     :db.install/_attribute :db.part/db}]
   [[:db/add :Property :type :Class]
    [:db/add :label :type :Property]
    [:db/add :FunctionalProperty :subclass :Property]
    [:db/add :InverseFunctionalProperty :subclass :Property]
    [:db/add :on-property :type :Property]
    [:db/add :on-property :range :Property]
    [:db/add :some-values-from :type :Property]
    [:db/add :some-values-from :range :Class]]])

;; ----------------------------------------
;; Inference rules
;; ----------------------------------------
(def ^:private class-propagation
  '[[[subclass ?t ?v]
     [?t :subclass ?v]]
    [[subclass ?t ?v]
     [?t :subclass ?u]
     [subclass ?u ?v]]])

(def ^:private type-propagation
  '[[[type ?o ?t]
     [?o :type ?t]]
    [[type ?o ?t]
     [subclass ?s ?t]
     [type ?o ?s]]])

(def ^:private restriction-classes
  '[[[type ?o ?c]
     [property ?c :some-values-from ?t]
     [property ?c :on-property ?op]
     [property ?o ?op ?x]
     [type ?x ?t]]])

(def ^:private subproperty-propagation
  '[[[subproperty ?p ?q]
     [?p :subproperty ?q]]

    [[subproperty ?p ?q]
     [?p :subproperty ?x]
     [subproperty ?x ?q]]])

(def ^:private property-attachment
  '[[[property ?o ?p ?v]
     [type ?p :Property]
     [?o ?p ?v]]

    [[property ?o ?p ?v]
     [subproperty ?q ?p]
     [property ?o ?q ?v]]])

(def ^:private inverse-property
  '[[[property ?o ?p ?v]
     [?p :inverseof ?q]
     [property ?v ?q ?o]]])

(def ^:private domain-inference
  '[[[type ?o ?t]
     [?o ?p]
     [?p :domain ?t]]

    [[type ?o ?t]
     [?o ?q]
     [subproperty ?q ?p]
     [?p :domain ?t]]])

(def ^:private range-inference
  '[[[type ?o ?t]
     [?x ?p ?o]
     [?p :range ?t]]

    [[type ?o ?t]
     [?x ?q ?o]
     [subproperty ?q ?p]
     [?p :range ?t]]])

(def ^:private equivalence-inference
  '[[[same-as ?s ?e]
     [(identity ?s) ?e]]
    [[same-as ?s ?e]
     [property ?x ?p ?s]
     [property ?x ?p ?e]
     [?p :type :FunctionalProperty]]
    [[same-as ?s ?e]
     [same-as ?s ?o]
     [same-as ?o ?e]]
    [[same-as ?s ?e]
     [property ?s ?p ?x]
     [property ?e ?p ?x]
     [?p :type :InverseFunctionalProperty]]])

(def inferences
  (concat type-propagation class-propagation restriction-classes subproperty-propagation property-attachment domain-inference range-inference equivalence-inference inverse-property))

;; ----------------------------------------
;; Query functions
;; ----------------------------------------
(defn qes
  "Query for entities. Follows the same syntax as datomic.api/q,
   but expects the query to return entity IDs. qes then returns
   a single set of entity IDs."
  [q db & args]
  (->> (apply d/q q db args)
       (map first)
       (into #{})))

(defn inhabitants
  "Find entities that belong to a class. Applies all inference rules.
   Returns a set of entity IDs.

   Class membership may be directly stated via a type-of property. It
   may be inferred via membership in a subclass. It may also be inferred
   on the basis of a property's domain or range."
  [db class]
  (qes '[:find ?lab
         :in $ % ?class
         :where
         [type ?e ?s]
         [?s :uri ?class]
         [?e :uri ?lab]]
       db
       inferences
       class))

(defn classes
  "Find the classes that an entity belongs to. Applies all inference rules,
   with the same semantics as inhabitants."
  [db node]
  (qes '[:find ?lab
         :in $ % ?node
         :where
         [?n :uri ?node]
         [type ?n ?cls]
         [?cls :uri ?lab]]
       db
       inferences
       node))

(defn subclasses
  "Find subclasses of the given class. Returns a set of entity IDs
   where the entities are the classes."
  [db class]
  (qes '[:find ?lab
         :in $ % ?class
         :where
         [subclass ?e ?c]
         [?c :uri ?class]
         [?e :uri ?lab]]
       db
       inferences
       class))

(defn subproperties
  "Find subproperties of the given property. Returns a set of entity IDs,
   where the entities are the properties."
  [db property]
  (qes '[:find ?ident
         :in $ % ?property
         :where
         [subproperty ?sub ?property]
         [?sub :db/ident ?ident]]
       db
       inferences
       property))

(defn dereference
  "Find the value of an object-valued property on an entity. Follows
   all inference rules. Returns the label (URI) of the target entity."
  [db subj prop]
  (qes '[:find ?lab
         :in $ % ?subj ?given
         :where
         [?s :uri ?subj]
         [property ?s ?given ?v]
         [?v :uri ?lab]]
       db
       inferences
       subj
       prop))

(defn dereferencev
  "Find the value of an immediate-valued property on an entity. Follows
   all inference rules. Returns the value directly."
  [db subj prop]
  (qes '[:find ?v
         :in $ % ?subj ?given
         :where
         [?s :uri ?subj]
         [property ?s ?given ?v]]
       db
       inferences
       subj
       prop))

(defn same-as
  "Find the entities which are equivalent to the given entity. Follows
   inference rules. Sameness may be explicitly stated, or it may be
   inferred via a property's domain or range semantics."
  [db subj]
  (qes '[:find ?equiv
         :in $ % ?subj
         :where
         [?s :uri ?subj]
         [same-as ?s ?e]
         [?e :uri ?equiv]]
       db
       inferences
       subj))

(defn identify
  "Return the identifying label (URI) for the given entity."
  [db subj]
  (qes '[:find ?l
         :in $ % ?subj
         :where
         [?s :uri ?subj]
         [property ?s :label ?l]]
       db
       inferences
       subj))

(defn- chain-clauses
  [initial-lvar terminal-lvar ps]
  (rest
   (reduce
    (fn [clauses p]
      (let [join-lvar (last (last clauses))
            next-join-lvar (if (identical? p (last ps)) terminal-lvar (gensym "?join"))]
        (conj clauses ['property join-lvar p next-join-lvar])))
    [[initial-lvar]]
    ps)))

(defn get-value
  "Get the data-valued result of following a chain of properties
   from the subject. prop-chain is a vector of property idents that must
   terminate in a data value (i.e., not an entity reference.)"
  [db subj prop-chain & extra]
  (qes (concat '[:find ?v
                 :in $ % ?subj
                 :where
                 [?s :uri ?subj]]
               (chain-clauses '?s '?v prop-chain)
               extra)
       db
       inferences
       subj))

(defn get-ovalue
  "Get the object-valued result of following a chain of properties
   from the subject. prop-chain is a vector of property idents that must
   terminate in a reference to an entity."
  [db subj prop-chain & extra]
  (qes (concat '[:find ?vlab
                 :in $ % ?subj
                 :where
                 [?s :uri ?subj]]
               (chain-clauses '?s '?v prop-chain)
               '[[?v :uri ?vlab]]
               extra)
       db
       inferences
       subj))

(defn has-class?
  "Returns true if the subject is a member of the class."
  [db subj class]
  (some #{class} (classes db subj)))

(defn has-any-class?
  "Returns true if any of the subject's classes are found in the set clss."
  [db subj clss]
  (intersection (classes db subj) clss))

;; ----------------------------------------
;; Declaring entities, classes, and properties
;; ----------------------------------------

(defn- penultimate [l] (first (rest (reverse l))))
(defn- at [ch] (if (= :as (penultimate ch)) (last ch) ch))

(defn realize-properties
  [db subjs & prop-chains]
  (apply merge-with merge
         (for [base    subjs
               chain   prop-chains
               :let    [loc (at chain)
                        v   (get-value db base chain)]
               :when   (not (empty? v))]
           {base {(if (= 1 (count loc)) (first loc) loc) (first v)}})))

(defn- id [p s]
  (let [h (hash s)]
    (d/tempid p (if (neg? h) h (- h)))))

(defn ->id
  "Compute an identifier that is 'relatively unique' for the entity name."
  [s]
  (id :onto s))

(defn ->aid
  "Compute an identifier that is 'relatively unique' for the property name."
  [s]
  (id :db.part/db s))

(defn bnode
  "Create a 'blank node', an intermediate entity whose
   identifier is of no interest."
  []
  (str (d/squuid)))

(declare t v)

(defn ->odatoms
  "Return datoms for the given entity or entities. If given an entity,
   return a collection of datoms. If given a collection of entities,
   flattens them all into datoms and creates a blank node to contain
   the collection."
  [e]
  (if (vector? e)
    (let [blank (bnode)]
      (mapcat (fn [[k v]] (t blank k v)) (partition 2 e)))
    [[:db/add (->id e) :uri e]]))

(defn t
  "Make a triple to relate the subject 's' to the object 'o' via the property 'p'."
  [s p o]
  (let [s-datoms (->odatoms s)
        o-datoms (->odatoms o)]
    (concat s-datoms
            [[:db/add (second (first s-datoms)) p (second (first o-datoms))]]
            o-datoms)))

(defn ->dtdatoms
  "Return datoms to relate subject 's' to the entity (or entities) 'e'
   via the property 'p'.

   If given an entity, it will be flattened into datoms. If
   given a collection of entities, they will all be flattened into
   datoms and a blank node will be added to contain the collection."
  [s p e]
  (if (vector? e)
    (let [blank (bnode)]
      (concat [[:db/add s p (->id blank)]]
              (mapcat (fn [[k q]] (v blank k q)) (partition 2 e))))
    [[:db/add s p e]]))

(defn v
  "Returns datoms to relate the subject 's' to the data value 'v'
   via the property 'p'."
  [s p v]
  (let [s-datoms (->odatoms s)]
    (concat s-datoms
            (->dtdatoms (second (first s-datoms)) p v))))

(defn +entity
  "Declare that a thing exists"
  [obj & {:as opts}]
  (merge {:db/id (->id obj)} opts))

(defn +class
  "Declare a new class"
  [cls & {:as opts}]
  (merge {:db/id (->id cls)
          :uri cls
          :type  :Class}
         opts))

(defn +member
  "Declare an entity to be a member of a class"
  [label & {:as opts}]
  (merge {:db/id (->id label)
          :uri label}
         opts))

(defn +property
  "Declare a new property."
  [property & {:as opts}]
  (merge {:db/id                 (->aid property)
          :db/ident              property
          :type                  :Property
          :db.install/_attribute :db.part/db}
         opts))

(def ^:private datomic-typeof
  {:string  :db.type/string
   :boolean :db.type/boolean
   :uuid    :db.type/uuid
   :long    :db.type/long
   :double  :db.type/double
   :uri     :db.type/uri
   :bigint  :db.type/bigint
   :bigdec  :db.type/bigdec
   :instant :db.type/instant
   :bytes   :db.type/bytes})

(def ^:private datomic-cardinality
  {:one  :db.cardinality/one
   :many :db.cardinality/many})

(defn dtproperty
  "Define a data-valued property."
  [property t cardinality]
  (assert (datomic-cardinality cardinality) (str "No cardinality mapping for " cardinality))
  (assert (datomic-typeof t) (str "No type mapping for " t))
  [(+property property :db/valueType (datomic-typeof t) :db/cardinality (datomic-cardinality cardinality))])

(defn oproperty
  "Define an object-valued property."
  [property cardinality]
  (assert (datomic-cardinality cardinality))
  [(+property property :db/valueType :db.type/ref :db/cardinality (datomic-cardinality cardinality))])

(defn oproperties
  "Define several object-valued properties."
  [& properties]
  (mapcat #(oproperty % :many) properties))

(defn type
  "Declare an entity to be a member of a class."
  [member cls]
  [(+class cls)
   (+member member :type #{(->id cls)})])

(defn subclass
  "Declare a subclass of a class"
  [subclass cls]
  [(+class cls)
   (+class subclass :subclass #{(->id cls)})])

(defn subproperty
  "Declare a subproperty of a property"
  [subproperty property]
  [(+property property)
   (+property subproperty :subproperty #{(->aid property)})])

(defn label
  "Define a new label property. This is a non-identifying string."
  [property]
  (concat (dtproperty property :string :one)
          (subproperty property :label)))

(defn domain
  "Declare the domain of a property to be the given class."
  [property cls]
  [(+class cls)
   (+property property :domain (->id cls))])

(defn range
  "Declare the range of a property to be the given class."
  [property cls]
  [(+class cls)
   (+property property :range (->id cls))])

(defn functional-property
  "Declare a new functional property."
  [prop]
  [(+property prop :type :FunctionalProperty)])

(defn inverse-functional-property
  "Declare a new inverse functional property"
  [prop]
  [(+property prop :type :InverseFunctionalProperty)])

(defn inverse-of
  "Declare a property 'inverse' to be the inverse of 'prop'."
  ([prop inverse]
     (inverse-of prop inverse :one))
  ([prop inverse cardinality]
     [(+property inverse
                 :db/cardinality (datomic-cardinality cardinality)
                 :db/valueType   :db.type/ref
                 :inverseof      (->aid prop))]))

(defn symmetric-property
  "Declare a property to be its own inverse."
  [prop]
  (concat
   (oproperty prop :many)
   (inverse-of prop prop)))

(defn some-values-from
  "Declare an inference, such that an entity which has 'property' _and_ the
   target of 'property' is a member of 'values-cls', then the entity is a
   member of 'cls'."
  [cls property values-cls]
  (list
   (+member cls :subclass "SomeValuesRestriction")
   (+class values-cls)
   (+property property)
   [:db/add (->id cls) :on-property (->aid property)]
   [:db/add (->id cls) :some-values-from (->id values-cls)]))

(defn- property-merge
  [& maps]
  (let [specificities {:Class 0
                       :Property 1
                       :FunctionalProperty 2
                       :InverseFunctionalProperty 2}
        kvs (mapcat seq maps)]
    (->> kvs
         (sort-by (comp specificities second))
         (into {}))))

(defn properties
  "Deduplicate and idempotentize a collection of property declarations.
   This is what you will use most often to declare properties in code.

   Example:
   (properties
     (dtproperty :short-description :string :one)
     (domain     :short-description \"SKU\")

     (dtproperty :street-date :instant :one)
     (domain     :street-date \"SKU\")

     (dtproperty :long-description :string :one))"
  [& props]
  (->> props
       (apply concat)
       distinct
       (group-by :db/id)
       vals
       (map #(apply property-merge %))))

(defn nodes
  "Deduplicate and idempotentize a collection of node declarations.
   This is what you should use to declare nodes in code.

   Example:
   (nodes
     (v uri :short-description sd)
     (v uri :long-description ld)
     (v uri :street-date avail))"
  [& nds]
  (->> nds
       (apply concat)
       distinct))

(def trait-schema
  (properties
   (oproperty  :function :many)
   (oproperty  :function-decl :many)
   (domain     :function-decl "Trait")
   (range      :function-decl "Function")
   (label      :function-name)
   (dtproperty :function-body :string :one)))

(defn bootstrap
  [conn]
  (doseq [tx (conj core-schemata trait-schema)]
    #_(prn tx)
    @(d/transact conn tx))
  conn)
