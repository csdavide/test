package it.doqui.libra.librabl.business.service.async;

import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.Map;

public class FeedbackAsyncOperation implements AsyncOperation<Map<String,Object>> {

    @Getter
    @Setter
    private Status status;

    @Override
    public boolean isDone() {
        return status == Status.SUCCESS || status == Status.FAILED;
    }

    @Getter
    @Setter
    private String operationId;


    @Getter
    @Setter
    private Map<String,Object> data;

    @Getter
    @Setter
    private ZonedDateTime createdAt;

    @Getter
    @Setter
    private ZonedDateTime updatedAt;

    @Override
    public Map<String, Object> get() {
        return data;
    }

    @Override
    public String getMessage() {
        if (data != null) {
            var mex = data.get("message");
            if (mex != null) {
                return mex.toString();
            }
        }

        return null;
    }
}
