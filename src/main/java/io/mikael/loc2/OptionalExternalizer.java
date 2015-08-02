package io.mikael.loc2;

import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.commons.util.Util;

import java.io.*;
import java.util.Optional;
import java.util.Set;

/**
 * Infinispan requires cacheable objects to implement Serializable or Externalizable,
 * which java.util.Optional unfortunately doesn't. See {@code resources/infinispan.xml}.
 */
public class OptionalExternalizer implements AdvancedExternalizer<Optional> {

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<? extends Optional>> getTypeClasses() {
        return Util.<Class<? extends Optional>>asSet(Optional.class);
    }

    @Override
    public Integer getId() {
        return 1234;
    }

    @Override
    public void writeObject(final ObjectOutput output, final Optional object) throws IOException {
        output.writeBoolean(object.isPresent());
        if (object.isPresent() &&
                (object.get() instanceof Serializable || object.get() instanceof Externalizable))
        {
            output.writeObject(object.get());
        }
    }

    @Override
    public Optional readObject(final ObjectInput input) throws IOException, ClassNotFoundException {
        if (input.readBoolean()) {
            return Optional.ofNullable(input.readObject());
        } else {
            return Optional.empty();
        }
    }
}
