$(document).ready(function() {
    var $orderSelect          = $('#orderSelect');
    var $bookingForm          = $('#bookingForm');
    var $inputHours          = $('input[name*=hours]');
    var $inputMinutes          = $('input[name*=minutes]');
    var $bookingMissingPage         = $('#bookingMissingPage');
    var $bookingSuccessPage         = $('#bookingSuccessPage');
    var $commentInput          = $('#commentInput');
    var commentInputDiv        = $commentInput.parent();
    var $commentMissingPage         = $('#commentMissingPage');
    var $thisBookingHours         = $('#thisBookingHours');
    var $thisBookingMinutes         = $('#thisBookingMinutes');
    var $summaryBookingHours         = $('#summaryBookingHours');
    var $summaryBookingMinutes         = $('#summaryBookingMinutes');
    var $thisBookingSuborder         = $('#thisBookingSuborder')
    var NOT_SELECTED = 'choose';
    
    
    $.ajax({
        url : 'StoreReportMobile',
        type : 'GET',
        datatype : 'json',
        success : function(data) {
        	var temp = data,
        	output = [];
        	if ($orderSelect.children().length == 1) {
        		$.each(temp, function(key, value) {
        			output.push('<option value="' + key + '" data-comreq="'+value[1]+'" >'
        					+ value[0] + '</option>');
        			});
        		$orderSelect.append(output.join('')).selectmenu('refresh');
        		}
        	}
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
    var prevb = {};
    var prevd = {};
    $inputHours.click(function() {
    	if (prevb && prevb.value == this.value) {
    		$(this).prop('checked', !prevb.status).checkboxradio('refresh');
    		}
    	prevb = {
    			value : this.value,
    			status : this.checked
    			};
    	});
    $inputMinutes.click(function() {
    	if (prevd && prevd.value == this.value) {
    		$(this).prop('checked', !prevd.status).checkboxradio('refresh');
    		}
    	prevd = {
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
    		$.mobile.changePage($commentMissingPage);
    		return false;
    	}
    	if (mis == true) {
    		$.mobile.changePage($bookingMissingPage);
    		return false;
    		}
    	//When all needed information present fire the ajax request to save the timereport 
    	$.ajax({
    		url : 'StoreReportMobile',
    		type : 'POST',
    		datatype : 'json',
    		data : $bookingForm.serialize(),
    		success : function(data) {
    			if (data.isValid) {
    				//Setting the values for the booking success page
    				$thisBookingHours.html(data.hours);
    				$thisBookingMinutes.html(data.minutes);
    				$summaryBookingHours.html(data.summaryHours);
    				$summaryBookingMinutes.html(data.summaryMinutes);
    				$thisBookingSuborder.html(data.suborder);
    				$.mobile.changePage($bookingSuccessPage);
    				// Reseting the state of radio buttons,submit button, select menu and comment input
    		    	$('input:checked').prop('checked',false).checkboxradio('refresh');
    		    	$orderSelect.val(NOT_SELECTED).selectmenu('refresh');
    		    	commentInputDiv.removeClass('required');
    		    	$commentInput.val('');
    				} else {
    				alert('Ein Fehler ist aufgetretten. Kontaktieren Sie bitte den Administator!');
    				}
    			}
    	});   	
    	return false;
    });
});