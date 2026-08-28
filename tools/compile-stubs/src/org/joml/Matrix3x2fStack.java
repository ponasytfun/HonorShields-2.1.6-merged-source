package org.joml;

/** Compile-only declaration for Minecraft's GUI matrix stack dependency. */
public class Matrix3x2fStack extends Matrix3x2f {
	public Matrix3x2fStack pushMatrix() { return this; }
	public Matrix3x2fStack popMatrix() { return this; }
}
