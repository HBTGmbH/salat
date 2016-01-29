var salatApp = angular.module('salatApp', [ 'salat.services' ]);
angular
		.module('salat.services', [])
		.factory(
				'Buchungen',
				function($http) {
					function query(callbackFunction, dateParam) {
						$http
						.get("http://localhost:8080/tb/rest/AuthenticationService/authenticate?username=kr&password=testrest");
						$http
								.get(
										"http://localhost:8080/tb/rest/buchungen/list?datum=29.01.2016&mitarbeiter=kr")
								.then(function(response) {
									callbackFunction(response.data);
								});
					}
					return {
						query : query
					};
				});
salatApp.config(function($httpProvider) {
	$httpProvider.defaults.withCredentials = true;
});