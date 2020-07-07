package ch.epfl.vlsc.analysis.partitioning.parser;

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class ProfileDataBase<Obj, T> {

    private Map<Obj, T> db;

    public ProfileDataBase() {
        this.db = new HashMap<>();
    }
    public void set(Obj object, T value) {
        if (this.db.containsKey(object)) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            "Overwriting set in " + this.getObjectName(object)));
        } else {
            this.db.put(object, value);
        }
    }
    public T get(Obj object) {
        if (this.db.containsKey(object)) {
            return this.db.get(object);
        } else {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            "Null get in " + getObjectName(object)));
        }

    }
    public Collection<T> values() {
        return this.db.values();
    }
    public Set<Obj> keySet() {

        return this.db.keySet();
    }

    public void forEach(BiConsumer<? super Obj, ? super T> action) {
        this.db.forEach(action);
    }
    abstract String getObjectName(Obj object);
}
