(ns onto.traits
  "Define expectations for behavior based on ontological inferences.

   Traits are to Onto Classes as Protocols are to Clojure Types

   Create a trait using `deftrait`. This declares the function
   definitions that must be supplied by any implemented of the
   trait. It is analogous to defprotocol.

   Attach a trait to an class using `extend-trait`. It is analogous to
   extend-type. `extend-trait` declares which Clojure functions will
   implement the trait functions for a given Onto class.

   Invoke a trait on an entity with `trait-invoke`.

   `trait-invoke` looks up the entity's classes, locates a trait with
   the method in question, resolves the function definition, and calls
   the function."
  (:require [datomic.api :as d]
            [onto.core :as o]))

(defprotocol Resolve
  "Supply a strategy for resolving a function reference to a real function at runtime."
  (resolve-fn [this fn-symb] "Locate the correct implementation of fn-symb for this member."))

(defprotocol Identity
  "Uses URIs to identify entities."
  (id [this] "Return the identity of this entity."))

(defprotocol Entity
  (entity [this db] "Return the value of an entity, given a basis in time."))

(defprotocol Member
  "Provide access to the classes in which this entity participates."
  (has-class? [this class] "Predicate that returns true when this member belongs to the named class")
  (classes    [this]       "Return the classes to which this member belongs at this point in time."))

(defn- prepare-for-call
  [cls fn-symb form-s]
  (let [form (read-string form-s)
        s    (if (sequential? form) (first form) form)
        tail (if (sequential? form) (rest form))]
    (assert (symbol? s))
    (when (namespace s)
      (require (symbol (namespace s))))
    (if-let [f (resolve s)]
      (fn [& args]
        (apply f (concat tail args)))
      (throw (IllegalArgumentException. (str "Function body for '" fn-symb "' on class '" cls
                                             "' refers to undefined implementation '" form-s "'"))))))

(defn- function-bodies
  [db classes fn-symb]
  (d/q '[:find ?subj ?nm ?body
         :in $ % ?nm [?subj ...]
         :where
         [?s :uri ?subj]
         [property ?s :function ?fn]
         [property ?fn :function-name ?nm]
         [property ?fn :function-body ?body]]
       db o/inferences (str fn-symb) (seq classes)))

(defn- resolve-trait-fn
  [db classes fn-symb]
  (let [candidates (function-bodies db classes fn-symb)]
    (case (count candidates)
      0  (throw (IllegalArgumentException. (str "No applicable function: " fn-symb
                                                " found in classes: " classes)))
      1  (apply prepare-for-call (first candidates))
      (throw (IllegalArgumentException. (str " Multiple classes in " classes " define " fn-symb
                                             ". Definitions include " (pr-str candidates)))))))

(defn trait-invoke
  "Invoke a trait function from an entity. `fn-symb` identifies the
  function name, unadorned by the name of the trait. Any additional
  arguments are passed to the trait function when it is invoked."
  [e fn-symb & args]
  (when-let [f (resolve-fn e fn-symb)]
    (apply f e args)))

(defn trait-fn-implementation
  "Attach the trait function to the Trait in the ontology. Attach
  values that define how the trait function is implemented."
  [tr class fn-symb body]
  (let [munged-name (str class "[" tr "]." (str fn-symb))]
    (o/nodes
     (o/t class :function munged-name)
     (o/v munged-name :function-name (str fn-symb))
     (o/v munged-name :function-body body))))

(defn trait*
  "Implementation behind the macro `deftrait`. You should call that
  instead."
  [nm & fns]
  (apply o/nodes
         (o/type (str nm) "Trait")
         (for [[fn-symb & [docstring]] fns]
           (let [blank (o/bnode)]
             (o/nodes
              (o/t (str nm) :function-decl blank)
              (o/v blank :function-name (str fn-symb))
              (when docstring
                (o/v blank :db/doc docstring)))))))

(defmacro deftrait
   "Declare a trait. The name is a Clojure symbol. `decls` are the
    method declarations.  Each one consists of a name and an optional
    docstring. This differs from defprotocol in that no arglist is
    allowed.

    Example:

        (deftrait Organic
          (methods \"Harvesting methods used to collect the item.\"))"
  [nm & decls]
  (let [qdecls (map (fn [x] `'~x) decls)]
    `(def ~(with-meta nm {:trait true})
       {:decls [~@qdecls]
        :txn   (trait* '~nm ~@qdecls)})))

(defn- must-resolve [nm]
  (if-let [tvar (resolve nm)]
    tvar
    (throw (Exception. (str "Unable to resolve symbol: " nm)))))

(defmacro extend-trait
  "Ascribe trait `nm` to class `cls`, providing implementations for
   each trait function. Each of `cls-decls` must be a list with the
   trait function name and the namespaced symbol of the function
   that supplies the behavior.

   The behavior function will later be called with a single argument,
   the entity on which the behavior was dispatched."
  [nm cls & cls-decls]
  (let [tvar        (must-resolve nm)
        trait-decls (:decls (var-get tvar))
        mneeded     (into #{} (map first trait-decls))
        mprovided   (into #{} (map first cls-decls))]
    (assert (:trait (meta tvar)) (str nm " is not a trait"))
    (assert (= mprovided mneeded) (str cls " must provide definitions for exactly the functions of " nm ". Needed: " mneeded ", provided: " mprovided))
    `(o/nodes
      ~@(for [[tfn-symb body] cls-decls]
          `(trait-fn-implementation (str ~tvar) ~cls '~tfn-symb ~(pr-str body))))))

(deftype OntologicalEntity [e]
  Identity
  (id [this] (:uri e))

  Member
  (has-class? [this class]   (o/has-class?     (d/entity-db e) (:uri e) class))
  (classes    [this]         (o/classes        (d/entity-db e) (:uri e)))

  Resolve
  (resolve-fn
    [this fn-symb]
    (resolve-trait-fn (d/entity-db e) (classes this) fn-symb)))

(defn- make-entity [dbe]
  (when dbe
    (->OntologicalEntity dbe)))

(deftype OntologyReference [uri]
  Identity
  (id [this] uri)

  Entity
  (entity [this db] (make-entity (d/entity db [:uri uri]))))

(defn refer [uri] (->OntologyReference uri))

(extend-protocol Entity
  java.lang.String
  (entity [this db] (make-entity (d/entity db [:uri this]))))
