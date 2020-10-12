package org.tb.restful.hello;

public class Message {
	
	private String message;
	private Person person;
	
	public Message(String message, Person person) {
		this.message = message;
		this.person = person;
	}

	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Person getPerson() {
		return person;
	}
	public void setPerson(Person person) {
		this.person = person;
	}
	
	

}
