package soac.java.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZippedUTF8String extends UTF8String {
	//Might want to precompute the hash here to avoid de/compression cost
	
	public static byte[] gzipBytes(byte[] in){
		ByteArrayOutputStream baos = new ByteArrayOutputStream(in.length);
		try {
			GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos);
            gzipOutputStream.write(in);
            gzipOutputStream.close();
		} catch(Exception e){
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}
	
	public GZippedUTF8String(String s) {
		super(gzipBytes(s.getBytes(utf8)));	
	}
	
	public GZippedUTF8String(CharSequence cs){
		this(cs.toString());
	}
	@Override
	public CharSequence subSequence(int start, int end) {
		return compactString(toString().subSequence(start, end));
	}
	@Override
	public String toString() {
		try {
			final ByteArrayInputStream bytein = new ByteArrayInputStream(data);
			final GZIPInputStream gzin = new GZIPInputStream(bytein);
			final InputStreamReader reader = new InputStreamReader(gzin, utf8);
			
			StringWriter writer = new StringWriter();
			char[] buffer = new char[1024];
		    for (int length = 0; (length = reader.read(buffer)) > 0;) {
		        writer.write(buffer, 0, length);
		    }
		    return writer.toString();
		} catch (Exception e){
			throw new RuntimeException(e);
		}
		
	}
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof GZippedUTF8String) {
			final GZippedUTF8String asGZCS = (GZippedUTF8String)obj;
			return obj == this || Arrays.equals(this.data, asGZCS.data);
		}
		return super.equals(obj);
	}
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
}
