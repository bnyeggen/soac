package soac.java.pav;

import java.util.Arrays;
import clojure.lang.APersistentVector;
import clojure.lang.IObj;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentStack;
import clojure.lang.IPersistentVector;
import clojure.lang.PersistentVector;

public class PersistentArrayVector extends APersistentVector implements IObj {
	public static final long serialVersionUID = 1L;
	// There is little point to making this implement IEditableCollection
	// directly,
	// since below 32 or so elements it's as fast or faster than a
	// TransientVector,
	// and above that you'd want to be using a TransientVector anyway, which
	// will
	// persist back to a PersistentVector and not a PersistentArrayVector
	// regardless of the length.
	// If this was integrated into PersistentVector the same way that
	// PersistentArrayMap is integrated into PersistentHashMap, the transition
	// would be seamless and it might be worth doing.
	final Object[] contents;
	final IPersistentMap _meta;
	public final static int PERSISTENT_VECTOR_THRESHOLD = 32;

	public PersistentArrayVector(Object[] contents, IPersistentMap meta) {
		super();
		this.contents = contents;
		this._meta = meta;
	}

	public static IPersistentCollection create(Iterable<?> i) {
		// Ideally we should pre-check the length if possible, and bail out to
		// faster bulk-inserts against PersistentVector.
		IPersistentCollection out = new PersistentArrayVector(new Object[] {},null);
		for (final Object o : i) out = out.cons(o);
		return out;
	}

	@Override
	public IPersistentMap meta() {
		return _meta;
	}

	@Override
	public IObj withMeta(IPersistentMap meta) {
		return new PersistentArrayVector(contents, meta);

	}

	@Override
	public IPersistentVector assocN(int i, Object o) {
		final Object[] newContents;
		if (i < contents.length) newContents = contents.clone();
		else if (i == contents.length) return cons(o);
		else throw new IndexOutOfBoundsException();
		newContents[i] = o;
		return new PersistentArrayVector(newContents, _meta);
	}

	@Override
	public IPersistentVector cons(Object o) {
		if (contents.length + 1 > PERSISTENT_VECTOR_THRESHOLD) {
			return PersistentVector.create(Arrays.asList(contents)).cons(o).withMeta(_meta);
		}
		final Object[] newContents = Arrays.copyOf(contents, contents.length + 1);
		newContents[newContents.length - 1] = o;
		return new PersistentArrayVector(newContents, _meta);
	}

	@Override
	public int count() {
		return contents.length;
	}

	@Override
	public IPersistentCollection empty() {
		return new PersistentArrayVector(new Object[] {}, null);
	}

	@Override
	public IPersistentStack pop() {
		return new PersistentArrayVector(Arrays.copyOf(this.contents, this.contents.length - 1), _meta);
	}

	@Override
	public Object nth(int i) {
		return contents[i];
	}
}