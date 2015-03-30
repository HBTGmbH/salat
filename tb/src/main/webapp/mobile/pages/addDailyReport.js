$(document).ready(function() {
//$(document).on('pageinit', function(){
	// Selectors
    var $orderSelect           = $('#orderSelect');
    var $bookingForm           = $('#bookingForm');
    var $inputHours            = $('input[name*=hours]');
    var $inputMinutes          = $('input[name*=minutes]');
    var $bookingMissingPage    = $('#bookingMissingPage');
    var $bookingSuccessPage    = $('#bookingSuccessPage');
    var $bookingOverviewPage   = $('#bookingOverviewPage');
    var $bookingOverviewDialog = $('#bookingOverviewDialog');
    var $bookingPage           = $('#bookingPage');
    var $bookingFooter         = $('#bookingFooter');
    var $bookingOverviewFooter = $('#bookingOverviewFooter');
    var $bookingSubmit         = $('#bookingSubmit');
    var $commentInput          = $('#commentInput');
    var commentInputDiv        = $commentInput.parent();
    var $commentMissingPage    = $('#commentMissingPage');
    var $thisBookingHours      = $('#thisBookingHours');
    var $thisBookingMinutes    = $('#thisBookingMinutes');
    var $thisBookingSuborder   = $('#thisBookingSuborder');
    var $hiddenTimereportId    = $('#hiddenTimereportId');
    //Constants
    var NOT_SELECTED           = 'choose';
    //States
    var minutesRadioState   = {};
    var hoursRadioState   = {};

    
    //Clickhandler for radio buttons
    var handleTimeRadios = function(state){
    	if (state && state.value == this.value) {
    		$(this).prop('checked', !state.status);
    		}
    	state = {
    			value : this.value,
    			status : this.checked
    			};
    };
    
	// Reseting the state of radio buttons,submit button, select menu and comment input
    var resetBookingForm = function() {
    	$bookingSubmit.prop('value','Speichern').prev('span').find('span.ui-btn-text').text("Buchen");
    	$('input:checked').prop('checked',false).checkboxradio('refresh');
    	minutesRadioState   = {};
    	hoursRadioState   = {};
    	$orderSelect.val(NOT_SELECTED).selectmenu('refresh');
    	commentInputDiv.removeClass('required');
    	$hiddenTimereportId.val('');
    	$commentInput.val('');
    };
    
    // Getting the list of the suborders and filling out the selector
    var getSuborders = function(){
    	$.ajax({
			url : '../../do/mobile/suborder/getSuborders',
			type : 'GET',
			datatype : 'json',
			data: '',
			success : function(data) {
				var temp = data,
				output = [];            	
        		$.each(temp, function(key, value) {
        			output.push('<option value="' + key + '" data-comreq="'+value[1]+'" >'
        					+ value[0] + '</option>');
        			});
        		$orderSelect.append(output.join('')).selectmenu('refresh');            		
        		}
		});    	
    };
    //Gets the clicked timereport
    var getTimereport = function(timereportId){		
		$.ajax({
			url:       '../../do/mobile/timereport/getTimereport',
			type:      'GET',
			datatype : 'json',
			data:      {'timereportId' : timereportId},
			success:   function(data){
				var temp = data;
				$orderSelect.val(temp.suborderId).selectmenu('refresh');
				$commentInput.val(temp.timereportComment);
				$.each($inputHours, function(){
					if($(this).val()==temp.timereportHours){
    					$(this).prop('checked', 'checked').checkboxradio('refresh');
    				}
				});
				$.each($inputMinutes, function(){
					if($(this).val()==temp.timereportMinutes){
    					$(this).prop('checked', 'checked').checkboxradio('refresh');
    				}
				});
			}			
		});    	
    };
    
   //Gets all timereports for this day and creates a list 
   var getTimereports = function(){
   	$.ajax({
        url :      '../../do/mobile/timereport/getTimereports',
        type :     'GET',
        datatype : 'json',
        success :  function(data) {
        	//Delete the content of the booking overview first
        	$bookingOverviewDialog.empty();
        	//if no booking were done this day before
        	if(!data){
		        $bookingOverviewDialog.append("<h2>Heute haben Sie noch keine Buchungen vorgenommen!</h2>");
            }
        	//Otherwise run through the json data and fill a list with all the bookings
        	var temp = data,
        	output = [];
    		$.each(temp, function(key, value) {
    			output.push('<li><a data-id="' + key +'">Auftrag: ' + value[0]+'-- ' + value[1] + ':' + value[2] + '</a></li>');
    		});       		
    		$(document.createElement('ul')).data('role','listview').data('inset', 'true')
    			.prop('id', 'BookingsOverviewList').html(output.join('')).appendTo($bookingOverviewDialog);
    		$('#BookingsOverviewList').listview();
    		
    		$(document).on('click', '#BookingsOverviewList a', function(){        		
        		var timereportId = $(this).data('id')
        		$hiddenTimereportId.val(timereportId);
        		$bookingSubmit.prop('value','Speichern').prev('span').find('span.ui-btn-text').text("Speichern");          		
        		$.mobile.changePage($bookingPage);
        		getTimereport(timereportId);
        		console.log($hiddenTimereportId.val());
        	});
        }
    });  
   };
    
    
    //If the selector isn't already filled fill it.
    if ($orderSelect.children().length == 1) {
    	getSuborders();    	
	}  
    

    $bookingPage.on('pagebeforeshow', function(){
        	$bookingFooter.find('a').first().removeClass('ui-btn-active').addClass('ui-btn-active');        	       	
    });    	
    
    
    
    $bookingOverviewPage.on('pagebeforeshow', function(){
    	$(this).find('a').last().removeClass('ui-btn-active').addClass('ui-btn-active');
    	resetBookingForm();
    	if($('#BookingsOverviewList').length > 0) {
    		$(document).off('click', '#BookingsOverviewList a');
    	}    	
    	getTimereports();    	
    });
    
    //Highlighting the comment input if comments are required.
    $orderSelect.change(function() {
          var state = $(this).find('option:selected').data('comreq');
          if(state == true){
        	  commentInputDiv.addClass('required');
          }
          else if(state == false){
        	  commentInputDiv.removeClass('required');
          }          
      });
    // Making minutes and hours radio buttons deselectable
    

    $inputHours.click(function() {
    	if (hoursRadioState && hoursRadioState.value == this.value) {
    		$(this).prop('checked', !hoursRadioState.status).checkboxradio('refresh');
    		}
    	hoursRadioState = {
    			value : this.value,
    			status : this.checked
    			};
    });
    $inputMinutes.click(function() {
    	if (minutesRadioState && minutesRadioState.value == this.value) {
    		$(this).prop('checked', !minutesRadioState.status).checkboxradio('refresh');
    		}
    	minutesRadioState = {
    			value : this.value,
    			status : this.checked
    			};
    });
    //Submitting the booking form
    $bookingForm.submit(function() {
    	var mis = false;
    	var state = $(this).find('option:selected').data('comreq');
    	var ciText= $commentInput.val();
        //Checking if time, suborder and if required the comment field are field out
    	if ($('input:checked').length == 0) {
    		mis = true;
    		}
    	if ($orderSelect.val() == NOT_SELECTED) {
    		mis = true;
    		}
    	if($('.required').children('input').val() == ''){
    		$.mobile.changePage($commentMissingPage, {role:'dialog'});
    		return false;
    	}
    	if (mis == true) {
    		$.mobile.changePage($bookingMissingPage, {role:'dialog'});
    		return false;
    		}
    	//When all needed information present fire the ajax request to save the timereport 
    	$.ajax({
    		url : '../../do/mobile/timereport/store',
    		type : 'POST',
    		datatype : 'json',
    		data : $bookingForm.serialize(),
    		success : function(data) {
    			if (data.isValid) {
    				//Setting the values for the booking success page
    				$thisBookingHours.html(data.hours);
    				$thisBookingMinutes.html(data.minutes);
    				$thisBookingSuborder.html(data.suborder);
    				$.mobile.changePage($bookingSuccessPage, {role:'dialog'});
    				resetBookingForm();
    				} else {
    				alert('Ein Fehler ist aufgetretten. Kontaktieren Sie bitte den Administator!');
    				}
    			}
    	});   	
    	return false;
    });
});