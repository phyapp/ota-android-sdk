package com.ota.otalib.utils;


import java.nio.ByteOrder;

public class HexString {
	final static char HEX_CHAR[] = { '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	/**
	 * Convert integer value to hexadecimal byte presentation
	 * 
	 * @param val
	 *            Value
	 * @return 2 digit hexadecimal string
	 */
	public static String hexIfyByte(int val) {
		return "" + HEX_CHAR[(val >>> 4) & 0x0F] + HEX_CHAR[val & 0x0F];
	}

	/**
	 * Convert byte value to hexadecimal byte presentation
	 * 
	 * @param val
	 *            Value
	 * @return 2 digit hexadecimal string
	 */
	public static String hexIfyByte(byte val) {
		return hexIfyByte((int) val & 0xFF);
	}

	/**
	 * Convert int value to hexadecimal short presentation
	 * 
	 * @param val
	 *            Value
	 * @return 4 digit hexadecimal string
	 */
	public static String hexIfyShort(int val) {
		return hexIfyByte((val >>> 8) & 0xFF) + hexIfyByte(val & 0xFF);
	}

	/**
	 * Convert int value to hexadecimal int presentation
	 * 
	 * @param val
	 *            Value
	 * @return 8 digit hexadecimal string
	 */
	public static String hexIfyInt(int val) {
		return hexIfyShort((val >> 16) & 0xFFFF) + hexIfyShort(val & 0xFFFF);
	}

	/**
	 * Convert byte array to hexadecimal string
	 * 
	 * @param buffer
	 *            Buffer with bytes
	 * @param delimiter
	 *            Delimiter to be inserted between bytes. Use 0 for none.
	 * @param length
	 *            Number of byte to convert
	 * @return String with hexadecimal data
	 */
	public static String hexifyByteArray(byte[] buffer, char delimiter,
                                         int length) {

		// Allocate buffer for 2 or 3 times the size in bytes, depending on
		// whether a delimiter
		// was given or not
		StringBuffer sb = new StringBuffer((length << 1)
				+ (delimiter == 0 ? 0 : length));

		for (int i = 0; i < length; i++) {
			sb.append(HEX_CHAR[(buffer[i] >>> 4) & 0x0F]);
			sb.append(HEX_CHAR[buffer[i] & 0x0F]);
			if ((delimiter != 0) && (i < length - 1)) {
				sb.append(delimiter);
			}
		}
		return sb.toString();
	}

	/**
	 * Convert byte array to hexadecimal string
	 * 
	 * @param buffer
	 *            Buffer with bytes
	 * @param delimiter
	 *            Delimiter to be inserted between bytes. Use 0 for none.
	 * @return String with hexadecimal data
	 */
	public static String hexifyByteArray(byte[] buffer, char delimiter) {
		return hexifyByteArray(buffer, delimiter, buffer.length);
	}

	/**
	 * Convert byte array to hexadecimal string
	 * 
	 * @param buffer
	 *            Buffer with bytes
	 * @return String with hexadecimal data
	 */
	public static String hexifyByteArray(byte[] buffer) {
		return hexifyByteArray(buffer, (char) 0, buffer.length);
	}

	/**
	 * Dump buffer in hexadecimal format with offset and character codes
	 * 
	 * @param data
	 *            Byte buffer
	 * @param offset
	 *            Offset into byte buffer
	 * @param length
	 *            Length of data to be dumped
	 * @param widths
	 *            Number of bytes per line
	 * @param indent
	 *            Number of blanks to indent each line
	 * @return String containing the dump
	 */
	public static String dump(byte[] data, int offset, int length, int widths,
                              int indent) {
		StringBuffer buffer = new StringBuffer(80);
		int i, ofs, len;
		char ch;

		if ((data == null) || (widths == 0) || (length < 0) || (indent < 0)) {
			throw new IllegalArgumentException();
		}

		while (length > 0) {
			for (i = 0; i < indent; i++) {
				buffer.append(' ');
			}

			buffer.append(hexIfyShort(offset));
			buffer.append("  ");

			ofs = offset;
			len = widths < length ? widths : length;

			for (i = 0; i < len; i++, ofs++) {
				buffer.append(HEX_CHAR[(data[ofs] >>> 4) & 0x0F]);
				buffer.append(HEX_CHAR[data[ofs] & 0x0F]);
				buffer.append(' ');
			}

			for (; i < widths; i++) {
				buffer.append("   ");
			}

			buffer.append(' ');
			ofs = offset;

			for (i = 0; i < len; i++, ofs++) {
				ch = (char) (data[ofs] & 0xFF);
				if ((ch < 32) || ((ch >= 127))) {
					ch = '.';
				}
				buffer.append(ch);
			}

			buffer.append('\n');

			offset += len;
			length -= len;
		}
		return buffer.toString();
	}

	/**
	 * Dump buffer in hexadecimal format with offset and character codes
	 * 
	 * @param data
	 *            Byte buffer
	 * @param offset
	 *            Offset into byte buffer
	 * @param length
	 *            Length of data to be dumped
	 * @param widths
	 *            Number of bytes per line
	 * @return String containing the dump
	 */
	public static String dump(byte[] data, int offset, int length, int widths) {
		return dump(data, offset, length, widths, 0);
	}

	/**
	 * Dump buffer in hexadecimal format with offset and character codes. Output
	 * 16 bytes per line
	 * 
	 * @param data
	 *            Byte buffer
	 * @param offset
	 *            Offset into byte buffer
	 * @param length
	 *            Length of data to be dumped
	 * @return String containing the dump
	 */
	public static String dump(byte[] data, int offset, int length) {
		return dump(data, offset, length, 16, 0);
	}

	/**
	 * Dump buffer in hexadecimal format with offset and character codes
	 * 
	 * @param data
	 *            Byte buffer
	 * @return String containing the dump
	 */
	public static String dump(byte[] data) {
		return dump(data, 0, data.length, 16, 0);
	}

	/**
	 * Parse string of hexadecimal characters into byte array
	 * 
	 * @param str
	 *            String to parse
	 * @return byte array containing the string
	 */
	public static byte[] parseHexString(String str) {

		ByteBuffer b = new ByteBuffer(str.length() / 2);
		int i = 0;
		int size = str.length();

		if (str.startsWith("0x")) {
			i += 2;
			size -= 2;
		}

		while (size > 0) {
			if (!Character.isLetterOrDigit(str.charAt(i))) {
				i++;
				size--;
			}

			if (size < 2) {
				throw new NumberFormatException(
						"Odd number of hexadecimal digits");
			}
			String toParse = str.substring(i, i + 2);
			b.append((byte) Integer.parseInt(toParse, 16));
			i += 2;
			size -= 2;
		}
		return b.getBytes();
	}

	// 将byte数组转换成16进制字符串
	public static String bytesToHex(byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			// 将每个byte转换成两位的16进制数，并添加到StringBuilder中
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	public static String parseStringHex(byte[] b) {

		String ret = "";

		for (int i = 0; i < b.length; i++) {

			String hex = Integer.toHexString(b[i] & 0xFF);

			if (hex.length() == 1) {

				hex = '0' + hex;
			}

			ret += hex.toUpperCase();

		}

		return ret;

	}

	public static String hexToString(String hex) {
		StringBuilder sb = new StringBuilder();
		for (int count = 0; count < hex.length() - 1; count += 2) {
			String output = hex.substring(count, (count + 2));    //grab the hex in pairs
			int decimal = Integer.parseInt(output, 16);    //convert hex to decimal
			sb.append((char) decimal);    //convert the decimal to character
		}
		return sb.toString();
	}

	public static String int2ByteString(int i){
		String hex = Integer.toHexString(i & 0xFF);

		if (hex.length() == 1) {

			hex = '0' + hex;
		}

		return hex;
	}

	public static int bytesToInt(byte[] b, final ByteOrder byteOrder) {
		return b.length == 4 ? java.nio.ByteBuffer.wrap(b).order(byteOrder).getInt() : java.nio.ByteBuffer.wrap(b).order(byteOrder).getShort();
	}

}
