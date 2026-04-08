package it.doqui.libra.librabl.application.model.properties;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.domain.model.files.ContentBasicDescriptor;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.domain.model.files.Streamable;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.IOUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Getter
@Setter
@ToString
public final class BufferDescriptor extends ContentBasicDescriptor implements PropertyObject, Streamable {
    @ToString.Exclude
    private byte[] data;

    @JsonSetter(nulls = Nulls.SKIP)
    private ContentDestination target = ContentDestination.FILE;

    public ContentStream asStream() {
        if (data != null) {
            var cs = new ContentStream();
            cs.mergeWith(this);
            cs.setSize((long) data.length);
            cs.setInputStream(new ByteArrayInputStream(data));
            cs.setTarget(target);
            return cs;
        }

        return null;
    }

    public static BufferDescriptor of(ContentStream cs) {
        var buffer = new BufferDescriptor();
        buffer.copyFrom(cs);
        buffer.setTarget(cs.getTarget());
        try {
            buffer.setData(IOUtils.readFully(cs.getInputStream()));
        } catch (IOException e) {
            throw new SystemException(e);
        }
        return buffer;
    }
}
