package org.visallo.core.model.textHighlighting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.visallo.core.model.termMention.TermMentionFor;
import org.visallo.web.clientapi.model.SandboxStatus;

import java.util.ArrayList;
import java.util.List;

public abstract class OffsetItem implements Comparable {
    public static final int VIDEO_TRANSCRIPT_INDEX_BITS = 12; // duplicated in org.visallo.web.clientapi.codegen.EntityApiExt
    public static final int VIDEO_TRANSCRIPT_OFFSET_BITS = 20; // duplicated in org.visallo.web.clientapi.codegen.EntityApiExt

    public abstract long getStart();

    public int getVideoTranscriptEntryIndex() {
        return (int) (getStart() >> VIDEO_TRANSCRIPT_OFFSET_BITS);
    }

    public int getVideoTranscriptEntryOffset(int compacted) {
        int offsetMask = (1 << VIDEO_TRANSCRIPT_INDEX_BITS) - 1;
        return compacted & offsetMask;
    }

    public abstract long getEnd();

    public abstract String getId();

    public abstract String getProcess();

    public abstract void setShouldBitShiftOffsetsForVideoTranscript(boolean shouldBitShiftOffsetsForVideoTranscript);

    public String getOutVertexId() {
        return null;
    }

    public String getResolvedToVertexId() {
        return null;
    }

    public String getResolvedFromTermMentionId() {
        return null;
    }

    public String getResolvedToTermMentionId() {
        return null;
    }

    public String getResolvedToEdgeId() {
        return null;
    }

    public abstract TermMentionFor getTermMentionFor();

    public abstract String getTermMentionForElementId();

    public abstract SandboxStatus getSandboxStatus();

    public abstract String getClassIdentifier();

    public JSONObject getInfoJson() {
        try {
            JSONObject infoJson = new JSONObject();
            infoJson.put("id", getId());
            infoJson.put("start", getStart());
            infoJson.put("end", getEnd());
            infoJson.put("outVertexId", getOutVertexId());
            infoJson.put("sandboxStatus", getSandboxStatus().toString());
            if (getResolvedToVertexId() != null) {
                infoJson.put("resolvedToVertexId", getResolvedToVertexId());
            }
            if (getResolvedFromTermMentionId() != null) {
                infoJson.put("resolvedFromTermMentionId", getResolvedFromTermMentionId());
            }
            if (getTermMentionForElementId() != null) {
                infoJson.put("termMentionForElementId", getTermMentionForElementId());
            }
            if (getResolvedToEdgeId() != null) {
                infoJson.put("resolvedToEdgeId", getResolvedToEdgeId());
            }
            if (getTermMentionFor() != null) {
                infoJson.put("termMentionFor", getTermMentionFor().toString());
            }
            infoJson.put("process", getProcess());
            return infoJson;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getCssClasses() {
        ArrayList<String> classes = new ArrayList<>();
        boolean resolved = getResolvedToVertexId() != null && getResolvedToEdgeId() != null;
        if (resolved) {
            classes.add("resolved");
        }
        TermMentionFor termMentionFor = getTermMentionFor();
        boolean resolvable = !resolved && termMentionFor == null;
        if (resolvable) {
            classes.add("resolvable");
        } else if (!resolved) {
            classes.add("jref");
        }
        if (resolvable || resolved) {
            classes.add("res");
        }
        if (getClassIdentifier() != null) {
            classes.add(getClassIdentifier());
        }
        return classes;
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("info", getInfoJson());

            JSONArray cssClasses = new JSONArray();
            for (String cssClass : getCssClasses()) {
                cssClasses.put(cssClass);
            }
            json.put("cssClasses", cssClasses);
            return json;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean shouldHighlight() {
        // Hide term mentions resolved to entities
        return getResolvedToTermMentionId() == null;
    }

    public String getTitle() {
        return null;
    }

    @Override
    public String toString() {
        return "id: " + getId() + ", start: " + getStart() + ", end: " + getEnd() + ", title: " + getTitle();
    }

    @Override
    public int compareTo(Object o) {
        if (!(o instanceof OffsetItem)) {
            return -1;
        }

        OffsetItem other = (OffsetItem) o;

        if (getOffset(getStart()) != getOffset(other.getStart())) {
            return getOffset(getStart()) < getOffset(other.getStart()) ? -1 : 1;
        }

        if (getOffset(getEnd()) != getOffset(other.getEnd())) {
            return getOffset(getEnd()) < getOffset(other.getEnd()) ? -1 : 1;
        }

        int termMentionForCompare = TermMentionFor.compare(getTermMentionFor(), other.getTermMentionFor());
        if (termMentionForCompare != 0) {
            return termMentionForCompare;
        }

        if (getResolvedToVertexId() == null && other.getResolvedToVertexId() == null) {
            return 0;
        }

        if (getResolvedToVertexId() == null) {
            return 1;
        }

        if (other.getResolvedToVertexId() == null) {
            return -1;
        }

        return getResolvedToVertexId().compareTo(other.getResolvedToVertexId());
    }

    public static long getOffset(long offset) {
        return offset & ((2 << (OffsetItem.VIDEO_TRANSCRIPT_OFFSET_BITS - 1)) - 1L);
    }
}
