# Onto

Ontological inferences for Datomic.

## Usage

Add the following dependency to `project.clj`:

```clojure
    {:dependencies [[com.cognitect/onto "0.1.0"]]}
```

## Description

This library provides a semantic layer for entities and attributes
stored in Datomic. The semantics are a subset of those found in RDFs
and OWL.

## Getting Started

The vars `onto.core/core-schemata` and `onto.traits/trait-schema` must
be transacted into your schema before you can use this. Each contains
a sequence of transactions. Because the schemata define new attributes
and use them, you cannot transact the schema all at once.

The `onto.core/bootstrap` function is a convenience function to load
the schemata.

## Entities and Classes

The core premise is that entities can be found to belong to one or
more classes. This is different than OO design, where you prescribe
class membership at creation time. Here an entity may gain or lose
classes by virtue of properties and values that are attached to it.

A Class is also an entity, therefore it is possible to create classes
of classes.

An entity is found to be part of a class when:

   * It is directly stated as such, via the `type` function.
   * It is a member of a subclass of the class
   * It is the target of a property whose range is the class.
   * It is the owner of a property whose domain is the class.

These rules all hold for subproperties of the property in question as
well.

## Properties

Properties are represented as Datomic attributes on entities. An
`oproperty` (object property) resolves to another entity. A
`dtproperty` (data property) resolves to a value. Both kinds of
property may be single- or multi-valued.

## Triples

Object-valued facts can be added by declaring triples with the
function `t`. A triple consists of a subject, property, and entity.

## Transacting

All the declarations return datoms. Properties and classes are
instantiated the first time they are mentioned. The function
`properties` helps weed out duplication in datoms from multiple
declarations. `nodes` does a similar job for datoms created by `v` and
`t`.

## Example

This is distilled from onto.examples.ecommerce.

```clojure
   (properties
     ;; A short description is a string
     (dtproperty :short-description :string :one)

     ;; Any entity that has a short description is a SKU
     (domain :short-description "SKU")

     ;; A street-date is a point in time
     (dtproperty :street-date :instant :one)

     ;; Any entity that has a street date is a SKU
     (domain :street-date "SKU")

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

     ;; Ask if the entity with label "sku:1234" is in fact a SKU
     (has-class? "sku:1234" "SKU")

     ;; Ask if that entity is sellable
     (has-class? "sku:1234" "Available")
```

## License
Copyright 2013 Relevance, Inc.

Copyright 2014-2016 Cognitect, Inc.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
which can be found in the file [epl-v10.html](epl-v10.html) at the root of this distribution.

By using this software in any fashion, you are agreeing to be bound by
the terms of this license.

You must not remove this notice, or any other, from this software.
