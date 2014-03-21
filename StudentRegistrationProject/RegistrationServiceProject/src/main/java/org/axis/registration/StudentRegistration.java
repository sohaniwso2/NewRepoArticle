package org.axis.registration;

public class StudentRegistration {

	private String studentID;
	private String name;
	private String address;
	private int contact;
	private String details;

	public void setUserDetails(String studentID, String name, String address,
			int contact) {
		this.studentID = studentID;
		this.name = name;
		this.address = address;
		this.contact = contact;
	}

	public String getCustomerRequest() {
		return details;

	}

}