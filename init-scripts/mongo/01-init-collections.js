// =====================================================================================================================
//  MongoDB bootstrap (infrastructure only). MongoDB has no tables to migrate: collections appear on first write.
//  This script only makes the databases and collections exist, so tools (Compass, mongosh) show them straight away.
//
//  Indexes are created by the services themselves (spring.data.mongodb.auto-index-creation: true), so the index
//  definitions live next to the @Document classes that need them and can never drift from the code.
//
//  Runs ONCE on an empty data volume (docker-entrypoint-initdb.d). Idempotent.
// =====================================================================================================================
function ensure(dbName, collections) {
  const target = db.getSiblingDB(dbName);
  const existing = target.getCollectionNames();
  collections.forEach(function (name) {
    if (existing.indexOf(name) === -1) {
      target.createCollection(name);
      print("created " + dbName + "." + name);
    }
  });
}

ensure("product-service", ["products"]);       // product-service
ensure("category-service", ["categories"]);    // category-service
