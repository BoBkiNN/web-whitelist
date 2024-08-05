package xyz.bobkinn.webwhitelist;

import java.util.ArrayList;

public class RollingList<T> extends ArrayList<T> {
    private final int maxCap;

    public RollingList(int maxCap){
        super(maxCap);
        if (maxCap == 0){
            throw new IllegalArgumentException("RollingList maxCap cannot be 0");
        }
        this.maxCap = maxCap;
    }

    @Override
    public boolean add(T t) {
        while (size() > maxCap - 1) {
            remove(0);
        }
        return super.add(t);
    }

    @Override
    public void add(int index, T element) {
        if (index < maxCap-1){
            if (index < size()) {
                remove(index);
            }
        }
        super.add(index, element);
    }
}
