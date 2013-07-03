$(document).ready(function() {
	var $loginForm		= $('#loginForm');
    var $usernameInput		= $('#usernameInput');
    var $passwordInput		= $('#passwordInput');		
    var $loginMissingPage	= $('#loginMissingPage');		
    var $loginErrorPage	= $('#loginErrorPage');
    var EMPTY   = '';
	
	$loginForm.submit(function() {
		//Check if username and password fields are filled out
		if($usernameInput.val()==null 
				|| $usernameInput.val()==EMPTY 
				|| $passwordInput.val()==null 
				|| $passwordInput.val()==EMPTY) {
			$.mobile.changePage($loginMissingPage);
	          return false;
		}
		
		$.ajax({
			url : 'LoginMobile',
			type : 'POST',
			datatype : 'json',
			data : $loginForm.serialize(),

			success : function(data) {
				if (data.isValid) {
					window.location.replace('addDailyReportMobile.html');
				}
				else {
					$.mobile.changePage($loginErrorPage);
				}
			}
		});

		return false;
	});
});