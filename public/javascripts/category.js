function Element(subcatagory, primarycatagory) {
        this.subcatagory = subcatagory;
        this.primarycatagory = primarycatagory;
}

var fields = [];
var result = "";

$(function () {

    var oldCategoryName = "";
    var oldSubCategoryName = "";
    var subCategoryName = "";
    var categoryName = "";
    var subCategory = "";

    $("#add-primary-category").click( function () {
        categoryName = $("#primary-category").val();
        addCategory(categoryName);
    });

    $("#add-sub-category").click( function(){
            var categoryName = $("#search-primary-category").val();
            subCategory = $("#sub-category").val();
            addSubCategory(categoryName,subCategory);
    });

    $("#modify-primary-category").on('input change', function(){
        $("#new-primary-category").show();
        oldCategoryName = $(this).val();
    });

    $("#modify-primary-category-btn").click( function() {
        $("#modify-primary-category").val("");
        var newCategoryName = $("#new-primary-category").val();
        modifyPrimaryCategory(oldCategoryName,newCategoryName);
    });

    /*$("#modify-sub-category").on('change', function() {
        $("#new-sub-category").show();
        oldSubCategoryName = $(this).val();
        categoryName = $("#categoryName").val();
    });*/

    $("#modify-sub-category-btn").click( function() {
       var newSubCategoryName = $("#new-sub-category").val();
       modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName);
    });

    $("#delete-primary-category").on('input change', function() {
        categoryName = $(this).val();
        subCategoryByPrimaryCategory(categoryName);
    });

    $("#delete-primary-category-btn").click( function() {
        console.log("---------------" + categoryName);
        deletePrimaryCategory(categoryName);
    });

    $("#delete-sub-category-btn").on('click', function() {
        $("#delete-sub-category-modal").modal('show');
    });

    $("#delete-sub-category-modal-yes-btn").click( function() {
          deleteSubCategory(categoryName, subCategoryName);
    });

    listSubCategoryWithPrimaryCategory();

    $("#datalist").keyup(function(event){
        var keyword = $("#datalist").val().toLowerCase();
        result = "";
        prepareResult(keyword)
    });

    function prepareResult(keyword){
        fields.forEach(function (element) {
               if(element.subcatagory.toLowerCase().includes(keyword)){
              result = result + '<div class="result" id="'+element.subcatagory+'-'+element.primarycatagory+'"><div class="sub-catagory wordwrap"><strong>'+element.subcatagory+'</strong></div><div class="primary-catagory">'+element.primarycatagory+'</div> </div>'
           }
        });
        $('#results-outer').html(result);
        $('#results-outer').show();
        result = "";
    }

    $("#drop-btn").click(function(){
         var keyword = $("#datalist").val().toLowerCase();
         if( keyword == ""){
            if($('#results-outer').is(":visible")){
                 $('#results-outer').hide();
            }else{
              prepareResult("");
            }
          }
         else{
           if($('#results-outer').is(":visible")){
               $('#results-outer').hide();
            }else{
              prepareResult(keyword);
            }
         }
    });

    $("#datalist").blur(function() {
         $('#results-outer').hide()
    });

    $(document).mouseup(function(e) {
        var container = $("#holder");
        if (!container.is(e.target) && container.has(e.target).length === 0)
        $('#results-outer').hide()
    });

    $("html").delegate( ".result", "mousedown", function() {
      var attribute = $(this).attr('id');
      var splits = attribute.split('-');
      console.log("splits = " + splits);
      subCategoryName = splits[0];
      categoryName = splits[1];
      alert(categoryName + " ..... " + subCategoryName);
      $("#datalist").val(splits[0]);
      topicMatchedWithCategory(categoryName, subCategoryName)
      $("#subcategory-sessions").show();
      $("#pair").val(attribute);
      $('#results-outer').hide();
    });

    $("html").delegate( ".result", "mouseover", function() {
       $(this).addClass('over');
    });

    $("html").delegate( ".result", "mouseleave", function() {
       $(this).removeClass('over');
    });

    /*for modify sub-category*/

    $("#mod-datalist").keyup(function(event){
            var keyword = $("#mod-datalist").val().toLowerCase();
            result = "";
            modPrepareResult(keyword)
        });

    function modPrepareResult(keyword){
            fields.forEach(function (element) {
                   if(element.subcatagory.toLowerCase().includes(keyword)){
                  result = result + '<div class="mod-result" id="'+element.subcatagory+'-'+element.primarycatagory+'"><div class="sub-catagory wordwrap"><strong>'+element.subcatagory+'</strong></div><div class="primary-catagory">'+element.primarycatagory+'</div> </div>'
               }
            });
            $('#mod-results-outer').html(result);
            $('#mod-results-outer').show();
            result = "";
        }

    $("#mod-drop-btn").click(function(){
             var keyword = $("#mod-datalist").val().toLowerCase();
             if( keyword == ""){
                if($('#mod-results-outer').is(":visible")){
                     $('#mod-results-outer').hide();
                }else{
                  modPrepareResult("");
                }
              }
             else{
               if($('#mod-results-outer').is(":visible")){
                   $('#mod-results-outer').hide();
                }else{
                  modPrepareResult(keyword);
                }
             }
        });

    $("#mod-datalist").blur(function() {
         $('#mod-results-outer').hide()
    });

    $(document).mouseup(function(e) {
        var container = $("#mod-holder");
        if (!container.is(e.target) && container.has(e.target).length === 0)
        $('#mod-results-outer').hide()
    });

    var newSubCategoryName = "";
    $("html").delegate( ".mod-result", "mousedown", function() {
          var attribute = $(this).attr('id');
          var splits = attribute.split('-');
          console.log("splits = " + splits);
          oldSubCategoryName = splits[0];
          categoryName = splits[1];
          alert(categoryName + " ..... " + subCategoryName);
          $("#mod-datalist").val(splits[0]);
          $("#new-sub-category").show();
          //newSubCategoryName = $("#new-sub-category").val();

          $("#mod-pair").val(attribute);
          $('#mod-results-outer').hide();
        });

        /*$("#modify-sub-category-btn").click( function() {
               var newSubCategoryName = $("#new-sub-category").val();
               modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName);
            });*/

        $("html").delegate( ".mod-result", "mouseover", function() {
           $(this).addClass('over');
        });

    $("html").delegate( ".mod-result", "mouseleave", function() {
           $(this).removeClass('over');
        });


});


function listSubCategoryWithPrimaryCategory() {

    jsRoutes.controllers.SessionsController.getCategory().ajax(
    {
        type: 'GET',
        processData: false,
        contentType: 'application/json',
        success: function(data) {

            var values =JSON.parse(data);
            var listOfData = [];
            console.log(values[0].subCategory);
            for(var i = 0; i< values.length; i++ ) {
               for(var j = 0;j< values[i].subCategory.length; j++) {

                  var elem = new Element(values[i].subCategory[j], values[i].categoryName);
                   fields.push(elem);
               }
            }
        }
});
}

function successMessageBox() {
    $("#success-message").show();
    $("#wrong-message").hide();
}

function wrongMessageBox() {
    $("#success-message").hide();
    $("#wrong-message").show();
}

function addCategory(categoryName) {
    jsRoutes.controllers.SessionsController.addPrimaryCategory(categoryName).ajax (
        {
            type: 'GET',
            processData: false,
            contentType: false,

            success: function(data) {
                successMessageBox();
                $("#primary-category").val("");
                document.getElementById("success-message").innerHTML = data;
                $("#categories").append("<option value='" + categoryName + "'>" + categoryName + "</option>")
            },
            error: function(er) {
                wrongMessageBox();
                document.getElementById("wrong-message").innerHTML = er.responseText
            }
        }
    )
}

function addSubCategory(categoryName,subCategory) {
    jsRoutes.controllers.SessionsController.addSubCategory(categoryName,subCategory).ajax (
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function(data) {
                successMessageBox()
                $("#sub-category").val("");
                document.getElementById("success-message").innerHTML = data;
                $("#subcategories").append("<option value='" + subCategory + "'>" + categoryName + "</option>");
            },
            error: function(er) {
                wrongMessageBox();
                document.getElementById("wrong-message").innerHTML = er.responseText;
            }
        }
    )
}

function modifyPrimaryCategory(oldCategoryName,newCategoryName) {

    jsRoutes.controllers.SessionsController.modifyPrimaryCategory(oldCategoryName,newCategoryName).ajax (
        {
            type: 'GET',
            processData: false,
            contentType: false,

            success: function(data) {
                successMessageBox();
                $("#new-primary-category").val("");
                document.getElementById("success-message").text(data);
            },
            error: function(er) {
                wrongMessageBox();
                $("#new-primary-category").val("");
                document.getElementById("wrong-message").innerHTML=er.responseText;
            }
        }
    )
}

function modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName){

    console.log(categoryName + "----" + oldSubCategoryName + "----" + newSubCategoryName)
    jsRoutes.controllers.SessionsController.modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName).ajax (
        {
            type:'GET',
            processData: false,
            contentType: false,

            success: function(data) {
                console.log(data);
                successMessageBox();
                $("#new-sub-category").show();
                document.getElementById("success-message").innerHTML = data;
                $("#subcategories").append("<option value='" + newSubCategoryName + "'>" + categoryName + "</option>");
                $("#new-sub-category").val("");
            },
            error: function(er) {
                wrongMessageBox();
                document.getElementById("wrong-message").innerHTML=er.responseText;
                $("#new-sub-category").val("");
             },
        }
    )
}

function deletePrimaryCategory(categoryName) {

    jsRoutes.controllers.SessionsController.deletePrimaryCategory(categoryName).ajax(
        {
            type:'GET',
            processData: false,
            contentType: 'application/json',
            success: function(data) {
                successMessageBox();
                /*var subCategories = JSON.parse(data);
                if(subCategories.length) {
                    console.log("Sub category = " + subCategories);
                    var restricted = '<div class="alert alert-warning">' +
                                     '<strong>Warning!</strong> Indicates a warning that might need attention.'+
                                     '</div>';
                    var subCategoryList = '<ul id="list-sub-categories">'
                    for(var i = 0 ; i < subCategories.length ; i++) {
                        subCategoryList += "<li>" + subCategories[i] + "</li>";
                    }
                    $("#category-sessions").html(subCategoryList);
                }*/
                console.log("data is = " + data)
                document.getElementById("success-message").innerHTML = data;
            },
            error: function(er) {
                wrongMessageBox();
                console.log("error is  = " + er.responseText)
                document.getElementById("wrong-message").innerHTML=er.responseText;
            }
        }
    )
}

function subCategoryByPrimaryCategory(categoryName) {

     jsRoutes.controllers.SessionsController.getSubCategoryByPrimaryCategory(categoryName).ajax(
         {
             type:'GET',
             processData: false,
             contentType: 'application/json',
             success: function(data) {
                 var subCategories = JSON.parse(data);

                 if(subCategories.length) {
                     console.log("Sub category = " + subCategories);
                     var restricted = '<div class="alert alert-warning">' +
                                      '<strong>Warning!</strong> Indicates a warning that might need attention.'+
                                      '</div>';
                     var subCategoryList = '<ul id="list-sub-categories">'
                     for(var i = 0 ; i < subCategories.length ; i++) {
                         subCategoryList += "<li>" + subCategories[i] + "</li>";
                     }
                     $("#category-sessions").html(subCategoryList);
                }

             },
             error: function(er) {
                $("#category-sessions").html("<p> * No sub category exists </p>");
             }
         }
     )
 }

function topicMatchedWithCategory(categoryName, subCategoryName){
    jsRoutes.controllers.SessionsController.getTopicsBySubCategory(categoryName, subCategoryName).ajax(
        {
            type:'GET',
            processData: false,
            contentType: 'application/json',
            success: function(data) {
                var topics = JSON.parse(data);
                console.log("topics = " + topics);
                if(topics.length) {
                    var sessions = '<ul id="list-sessions">';
                    for(var i = 0 ; i < topics.length ; i++) {
                        sessions += "<li>" + topics[i] + "</li>";
                    }
                    sessions += "</ul>";
                    $("#subcategory-sessions").html(sessions);
                } else {
                    console.log("NO session");
                    $("#no-sessions").remove();
                    var noSessions = '<label id= "no-sessions">No sessions exists!</label>'
                    $("#subcategory-sessions").before(noSessions);
                    $("#subcategory-sessions").hide();
                }
            },
            error: function(er) {
            }
        }
    )
}

function deleteSubCategory(categoryName, subCategoryName) {

    console.log("inside delete sub category js fun" + categoryName + "bbbbbb " +subCategoryName)

    jsRoutes.controllers.SessionsController.deleteSubCategory(categoryName, subCategoryName).ajax(
        {
            type:'GET',
            processData: false,
            contentType: false,

            success: function(data) {
                successMessageBox();
                $("#delete-sub-category").val("");
                document.getElementById("success-message").innerHTML = data;
                $("#subcategory-sessions").hide();
            },
            error: function(er) {
                wrongMessageBox();
                document.getElementById("wrong-message").innerHTML=er.responseText;
                $("#delete-sub-category").val("");
            }
        }
    )
}
