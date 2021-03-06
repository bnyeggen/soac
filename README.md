Java object overhead can be killer when dealing with large amounts of data. Because of the way Java allocates arrays, the only real way to get around the object overhead for a large number of small objects is to represent them as a Struct-Of-(primitive) Arrays, rather than an Array (or Seq)-Of-Structs.

You can think of hash tables as a specialization of this, since they store a sequence of key-value pairs, arranged for fast lookup/insertion/removal.

We provide a few families of data structures to deal with these issues:

- The SOAs (mutable, immutable via COW on arrays, and immutable/persistent via Clojure vector-ofs) in soac.soa. They are essentially code-efficient ways to support an object composed of multiple primitive "columns", rather than a list of "rows" of objects, which would each have additional object overhead. The immutable version is specialized for the case where it is mostly grow-and-use; as long as you always add to the "end" of the SOA, all referers can share views of the earlier parts by tracking their offsets. "Modifying" before the end, or adding to a view before the "real" end of the array, will result in independent copies being made. If
that's your use case, you may be better off using the persistent vector-SOA vesion.

- The hash tables (both maps and sets) in soac.hopscotch. These use hopscotch hashing, an algorithm which guarantees that elements, if they exist, will be within a set number of positions of the "optimal" insert point (which is nice if you're seeking over the packed leaves of a tree - you potentially avoid a lot of pointer-traversal overhead). You should expect large memory savings (the primitive-backed hash sets, for instance, take between 1/5 and 1/6 of the space of an equivalent PersistentHashSet) at the cost of some additional insertion time. Lookups and removals should in general be as fast or faster. Speed as a whole will increase once clojure.core.Vec supports transients.

- The array-backed persistent vectors in soac.arrvec. These have specializations both for Objects and primitives that are more compact and faster than the Clojure data structures for small vectors, but have all their persistency guarantees. They're implemented by a primitive array that is simply copied when "modified". The built-in data structures do this as well at the leaves, as well as the internal nodes, so the array-backed versions are actually more efficient for small data sizes (e.g., for a 31-long int array, conj'ing onto an array-backed version takes roughly 68% of the time of the built-in version). They evolve to the built-in data structures when they
contain enough elements that copy-on-write is no longer efficient. Currently we set that at the equivalent of 256 bytes or 32 object references.

- The interning facilities of soac.intern. This surfaces the ability to deduplicate persistent data structures with no loss of flexibility by replacing equivalent objects with pointers to the same underlying instance.

- The miscellaneous compact data structures (UTF8-based CharSequences with optionally shared structure & compression, more compact persistent linked lists) of soac.java.util

TODO:

- Transient support for hopscotch (ideally via adding transient support to gvec, which I'm told will happen eventually)
- Persistent bloom filter


LICENSE:

Distributed under the Eclipse Public License v1.0
