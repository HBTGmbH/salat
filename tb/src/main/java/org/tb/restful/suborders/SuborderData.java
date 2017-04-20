package org.tb.restful.suborders;

public class SuborderData {
	
	private long id;
	private String label;
	private boolean commentRequired;

	public SuborderData(long id, String label, boolean commentRequired) {
		this.id = id;
		this.label = label;
		this.commentRequired = commentRequired;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public boolean isCommentRequired() {
		return commentRequired;
	}

	public void setCommentRequired(boolean commentRequired) {
		this.commentRequired = commentRequired;
	}

}
