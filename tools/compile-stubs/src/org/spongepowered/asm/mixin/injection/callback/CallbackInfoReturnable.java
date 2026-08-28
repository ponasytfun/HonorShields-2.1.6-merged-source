package org.spongepowered.asm.mixin.injection.callback;

public class CallbackInfoReturnable<T> extends CallbackInfo {
    public T getReturnValue() { return null; }
	public float getReturnValueF() { return 0.0F; }
    public void setReturnValue(T value) {}
}
