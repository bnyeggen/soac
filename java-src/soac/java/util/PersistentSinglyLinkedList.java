package soac.java.util;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import clojure.lang.IHashEq;
import clojure.lang.IPersistentCollection;
import clojure.lang.ISeq;
import clojure.lang.RT;
import clojure.lang.Sequential;
import clojure.lang.Util;

//Basically identical to a Cons, except no tracking of count/hash/hasheq
//This will tend to be slower; it's purely intended to be the most compact
//persistent linked list possible.
public class PersistentSinglyLinkedList implements ISeq, Sequential, IHashEq {
	private final Object _first;
	private final PersistentSinglyLinkedList _more;
	public final static Empty EMPTY = new Empty();

	//This is distinguished from PersistentList.Empty only by the fact that
	//cons'ing generates a PersistentSinglyLinkedList.
	static class Empty implements ISeq {
		@Override
		public ISeq cons(Object o) {
			return new PersistentSinglyLinkedList(o);
		}
		@Override
		public int count() { return 0; }
		@Override
		public IPersistentCollection empty() {
			return this;
		}
		@Override
		public boolean equiv(Object o) { return equals(o); }
		@Override
		public boolean equals(Object o) {
			return (o instanceof Sequential || o instanceof List) && RT.seq(o) == null;
		}
		@Override
		public Object first() { return null; }
		@Override
		public ISeq more() { return this; }
		@Override
		public ISeq next() { return null; }
		@Override
		public ISeq seq() { return null; }
		@Override
		public int hashCode() { return 1; }
	}
	
	public PersistentSinglyLinkedList(Object first) {
		this(first, null);
	}
	
	public PersistentSinglyLinkedList(Object first, PersistentSinglyLinkedList more) {
		this._first = first;
		this._more = more;
	}
	
	public static ISeq createFromList(List<?> ls){
		//Have add elements in reverse since cons adds to the head.
		ISeq out = EMPTY;
		for (ListIterator<?> i = ls.listIterator(ls.size()); i.hasPrevious();) {
			out = out.cons(i.previous());
		}
		return out;
	}
	
	public static ISeq createFromSeq(ISeq seq) {
		if (seq == null)
			return EMPTY;
		final ArrayList<Object> tmp = new ArrayList<>();
		for (; seq != null; seq = seq.next())
			tmp.add(seq.first());
		return createFromList(tmp);
	}

	@Override
	public Object first() {
		return _first;
	}
	
	@Override
	public ISeq more() {
		if (_more==null) return EMPTY;
		return _more;
	}
	@Override
	public ISeq next() {
		return more().seq();
	}
	@Override
	public int count() {
		return 1 + RT.count(_more);
	}
	@Override
	public IPersistentCollection empty() {
		return EMPTY;
	}
	@Override
	public PersistentSinglyLinkedList seq() {
		return this;
	}
	@Override
	public ISeq cons(Object o) {
		return new PersistentSinglyLinkedList(o, this);
	}

	@Override
	public boolean equiv(Object obj) {
		if (!(obj instanceof Sequential || obj instanceof List))
			return false;
		ISeq ms = RT.seq(obj);
		for (ISeq s = seq(); s != null; s = s.next(), ms = ms.next()) {
			if (ms == null || !Util.equiv(s.first(), ms.first()))
				return false;
		}
		return ms == null;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Sequential || obj instanceof List))
			return false;
		ISeq ms = RT.seq(obj);
		for (ISeq s = seq(); s != null; s = s.next(), ms = ms.next()) {
			if (ms == null || !Util.equals(s.first(), ms.first()))
				return false;
		}
		return ms == null;
	}

	@Override
	public int hashCode() {
		int hash = 1;
		for (ISeq s = seq(); s != null; s = s.next()) {
			hash = 31 * hash + (s.first() == null ? 0 : s.first().hashCode());
		}
		return hash;
	}

	@Override
	public int hasheq() {
		int hash = 1;
		for (ISeq s = seq(); s != null; s = s.next()) {
			hash = 31 * hash + Util.hasheq(s.first());
		}
		return hash;
	}	
}