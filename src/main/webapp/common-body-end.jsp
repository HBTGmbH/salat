<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
</div>
</div>
</div>
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.1.3/js/bootstrap.bundle.min.js"></script>
<script src="/scripts/select2.full.min.js"></script>
<script>
  $(document).ready(function () {

    $('.navigation > h1').on('click', function () {
      $('.navigation').toggleClass('active');
    });

    $(".make-select2").select2({
      dropdownAutoWidth: true,
      width: 'auto'
    });

  });
</script>
