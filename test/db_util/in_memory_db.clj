(ns db-util.in-memory-db
  "Avoid test coupling and fragile seed data setups by building a
   clean in-memory database for each test execution. The database will
   be fully initialized with the same schema as the runtime system
   will use.

   Tests can access a dynamic var *conn* that will hold the connection
   to the test database. Note that this assumes that all data access
   fns accept connections or databases as arguments, rather than
   referencing global vars."
  (:require [datomic.api :as d]))


(defprotocol DBSource
  (database [source] "Return a database from source"))

(extend-protocol DBSource
  datomic.Connection
  (database [this] (d/db this))
  datomic.Database
  (database [this] this))

(defn using
  [dbsource & txns]
  (reduce (fn [db tx]
            (try (:db-after (d/with db tx))
                 (catch Throwable e
                   (println "Error transacting " tx)
                   (throw e))))
          (database dbsource)
          txns))

(def ^:dynamic *conn*)

(defonce fixed-in-mem-db (atom nil))

(def fixed-in-mem-db-uri "datomic:mem://this-is-for-tests")

(defn- apply-bootstrap
  [uri bootstrap-fn]
  (d/create-database uri)
  (let [cxn (d/connect uri)]
    (bootstrap-fn cxn)
    cxn))

(defn reset-fixed-in-mem-db
  "When using a long-lived in-memory database, you may need
   to manually clear it. This function does that."
  [bootstrap-fn]
  (when @fixed-in-mem-db
    (d/delete-database fixed-in-mem-db-uri)
    (reset! fixed-in-mem-db (apply-bootstrap fixed-in-mem-db-uri bootstrap-fn))))

(defn init-fixed-in-mem-db
  "Create a long-lived in-memory database. You should only do
   this temporarily to debug a problem. Otherwise, the presence
   of existing data will render your tests nonrepeatable."
  [bootstrap-fn]
  (if @fixed-in-mem-db
    @fixed-in-mem-db
    (reset! fixed-in-mem-db (apply-bootstrap fixed-in-mem-db-uri bootstrap-fn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn with-fixed-in-mem-db
  "Use this an a :once fixture to use a single database
   across all tests, or use it as an :each fixture to
   consume tons of memory and give each test function its own
   private database."
  [bootstrap-fn]
  (fn [f]
    (binding [*conn* (init-fixed-in-mem-db bootstrap-fn)]
      (f))))

(defn with-disposable-in-mem-db
  "Use this to give each test its own in-memory database.
   Each database will be created with a unique URI and will never
   be used again.

   The database will be fully initialized with the same schema
   as the runtime system will use.

   This is good for test isolation, but hard for debugging. To debug,
   temporarily swap this out for a fixed in-memory db.

   bootstrap-fn must be a function that takes a connection."
  [bootstrap-fn]
  (fn [f]
    (let [uri (str (gensym "datomic:mem://"))]
      (binding [*conn* (apply-bootstrap uri bootstrap-fn)]
        (f)))))
