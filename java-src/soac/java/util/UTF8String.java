package soac.java.util;

import java.nio.charset.Charset;

public class UTF8String implements CharSequence {
	final byte[] data;
	final static Charset utf8 = Charset.forName("UTF-8");
	
	//Returns either a UTF8String, or a GZippedUTF8String, whichever is smaller
	public static UTF8String compactString(CharSequence cs){
		final UTF8String asUTF8 = new UTF8String(cs);
		final GZippedUTF8String asGZUTF8 = new GZippedUTF8String(cs);
		return asUTF8.data.length > asGZUTF8.data.length ? asGZUTF8 : asUTF8;
	}
	
	public UTF8String(String s) {
		data = s.getBytes(utf8);
	}
	public UTF8String(CharSequence c){
		this(c.toString());
	}

	public UTF8String(byte[] data) {
		this.data = data;
	}
	
	@Override
	public char charAt(int index) {
		return toString().charAt(index);
	}
	@Override
	public int length() {
		return toString().length();
	}
	@Override
	public CharSequence subSequence(int start, int end) {
		return new UTF8String(toString().subSequence(start, end));
	}
	
	//This refers to whole codepoints, not chars as for subSequence
	public UTF8SharedDataString sharedSubSequence(int start, int end){
		//1, 2, and 3-byte UTF8 sequences are represented by a single UTF16 char
		//4-byte sequences are represented as a surrogate pair
		int bytePos = 0;
		int ct = 0;
		while(ct<start){
			bytePos += bytesInCodepointAt(bytePos);
			ct++;
		}
		final int startBytePos = bytePos;
		while(ct<end){
			bytePos += bytesInCodepointAt(bytePos);
			ct++;
		}
		final int endBytePos = bytePos;
		return new UTF8SharedDataString(data, startBytePos, endBytePos-startBytePos);
	}
	
	public int bytesInCodepointAt(int pos){
		if((data[pos] & 0x80) == 0) return 1; 
		if((data[pos] & 0xF0) == 0xF0) return 4;
		if((data[pos] & 0xE0) == 0xE0) return 3;
		if((data[pos] & 0xC0) == 0xC0) return 2;
		//If wer'e somehow in the middle of a codepoint representation, backtrack
		if((data[pos] & 0x80) == 0x80) return bytesInCodepointAt(pos-1);
		
		throw new IllegalArgumentException("Malformed UTF8 data");
	}
	
	@Override
	public String toString() {
		return new String(data, utf8);
	}
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof UTF8String){
			return contentEquals((CharSequence)obj);
		}
		return false;
	}
	
	public boolean contentEquals(CharSequence c){
		return toString().equals(c.toString());
	}
	
	public byte[] getBytes(){
		return data.clone();
	}
}