package soac.java.pav;

import java.util.Arrays;

import clojure.core.ArrayManager;
import clojure.core.Vec;
import clojure.core.VecNode;
import clojure.lang.APersistentVector;
import clojure.lang.IObj;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.RT;

public class PersistentCharArrayVector extends APersistentVector implements IObj {
	public static final long serialVersionUID = 1L;
	final char[] contents;
	final IPersistentMap _meta;
	public final static int PERSISTENT_VECTOR_THRESHOLD = 128;
	
	final static ArrayManager am = (ArrayManager)RT.var("clojure.core", "ams").invoke(Keyword.intern("char"));
	final static Vec EMPTY_VEC =  new Vec(am, 0, 5, new VecNode(null, new Object[32]), am.array(0), null);
	
	PersistentCharArrayVector(char[] contents, IPersistentMap meta) {
		super();
		this.contents = contents;
		this._meta = meta;
	}
	
	public static IPersistentCollection create(Iterable<?> i){
		IPersistentCollection out = new PersistentCharArrayVector(new char[] {},null);
		for (final Object o : i) out = out.cons(o);
		return out;
	}
	
	@Override
	public IPersistentMap meta() {
		return _meta;
	}

	@Override
	public IObj withMeta(IPersistentMap meta) {
		return new PersistentCharArrayVector(contents, meta);
	}

	@Override
	public IPersistentVector assocN(int i, Object o) {
		final char[] newContents;
		if (i < contents.length) newContents = contents.clone();
		else if (i == contents.length) return cons(o);
		else throw new IndexOutOfBoundsException();
		newContents[i] = RT.charCast(o);
		return new PersistentCharArrayVector(newContents, _meta);
	}

	@Override
	public IPersistentVector cons(Object o) {
		if (contents.length + 1 > PERSISTENT_VECTOR_THRESHOLD) {
			IPersistentVector out = (IPersistentVector)EMPTY_VEC.withMeta(_meta);
			for (char b : contents) out = out.cons(b);
			return out.cons(o);
		}
		final char[] newContents = Arrays.copyOf(contents, contents.length + 1);
		newContents[newContents.length - 1] = RT.charCast(o);
		return new PersistentCharArrayVector(newContents, _meta);
	}

	@Override
	public int count() {
		return contents.length;
	}

	@Override
	public PersistentCharArrayVector empty() {
		return new PersistentCharArrayVector(new char[] {}, null);
	}

	@Override
	public PersistentCharArrayVector pop() {
		return new PersistentCharArrayVector(Arrays.copyOf(this.contents, this.contents.length - 1), _meta);
	}

	@Override
	public Object nth(int i) {
		return contents[i];
	}
}
