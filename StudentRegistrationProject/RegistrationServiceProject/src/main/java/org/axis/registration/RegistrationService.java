package org.axis.registration;

public class RegistrationService {

	public StudentRegistration createrequest(String studentID, String name,
			String address, int contact) {
		StudentRegistration details = new StudentRegistration();
		details.setUserDetails(studentID, name, address, contact);
		return details;
	}

}
