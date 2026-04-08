package it.doqui.libra.librabl.foundation.async;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.Map;

public class FeedbackAsyncOperation implements AsyncOperation<Map<String,Object>> {

    @Getter
    @Setter
    private JobStatus status;

    @Getter
    @Setter
    private String jobId;

    @Getter
    @Setter
    @JsonIgnore
    private Map<String,Object> data;

    @Getter
    @Setter
    private ZonedDateTime createdAt;

    @Getter
    @Setter
    private ZonedDateTime updatedAt;

    @Override
    public Map<String, Object> getResult() {
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
