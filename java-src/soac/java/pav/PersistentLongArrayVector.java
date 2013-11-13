package soac.java.pav;

import java.util.Arrays;

import clojure.core.ArrayManager;
import clojure.core.Vec;
import clojure.core.VecNode;
import clojure.lang.APersistentVector;
import clojure.lang.IObj;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentStack;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.RT;

public class PersistentLongArrayVector extends APersistentVector implements IObj {
	final long[] contents;
	final IPersistentMap _meta;
	public final static int PERSISTENT_VECTOR_THRESHOLD = 32;
	
	final static ArrayManager am = (ArrayManager)RT.var("clojure.core", "ams").invoke(Keyword.intern("long"));
	final static Vec EMPTY_VEC =  new Vec(am, 0, 5, new VecNode(null, new Object[32]), am.array(0), null);
	
	PersistentLongArrayVector(long[] contents, IPersistentMap meta) {
		super();
		this.contents = contents;
		this._meta = meta;
	}
	
	public static IPersistentCollection create(Iterable<?> i){
		IPersistentCollection out = new PersistentLongArrayVector(new long[] {},null);
		for (final Object o : i) out = out.cons(o);
		return out;
	}
	
	@Override
	public IPersistentMap meta() {
		return _meta;
	}

	@Override
	public IObj withMeta(IPersistentMap meta) {
		return new PersistentLongArrayVector(contents, meta);
	}

	@Override
	public IPersistentVector assocN(int i, Object o) {
		final long[] newContents;
		if (i < contents.length) newContents = contents.clone();
		else if (i == contents.length) return cons(o);
		else throw new IndexOutOfBoundsException();
		newContents[i] = RT.longCast(o);
		return new PersistentLongArrayVector(newContents, _meta);
	}

	@Override
	public IPersistentVector cons(Object o) {
		if (contents.length + 1 > PERSISTENT_VECTOR_THRESHOLD) {
			IPersistentVector out = (IPersistentVector)EMPTY_VEC.withMeta(_meta);
			for (long b : contents) out = out.cons(b);
			return out.cons(o);
		}
		final long[] newContents = Arrays.copyOf(contents, contents.length + 1);
		newContents[newContents.length - 1] = RT.longCast(o);
		return new PersistentLongArrayVector(newContents, _meta);
	}

	@Override
	public int count() {
		return contents.length;
	}

	@Override
	public PersistentLongArrayVector empty() {
		return new PersistentLongArrayVector(new long[] {}, null);
	}

	@Override
	public IPersistentStack pop() {
		return new PersistentLongArrayVector(Arrays.copyOf(this.contents, this.contents.length - 1), _meta);
	}

	@Override
	public Object nth(int i) {
		return contents[i];
	}

}
