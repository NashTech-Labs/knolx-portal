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

    $("#modify-sub-category").on('input change', function() {
        $("#new-sub-category").show();
        oldSubCategoryName = $(this).val();
        categoryName = $("#categoryName").val();
    });

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


    });

    $("#delete-sub-category-modal-yes-btn").click( function() {

          deleteSubCategory(categoryName, subCategoryName);
    });

    listData();
    function listData(){
        var id;
        $('#sub-Categorya option').each(function(i, e){
            id = $(this).attr("id", "id_" + i).appendTo(this);
        });
        }

        $("input[name=sub-Catgeoryrtyty]").focusout(function(){

        });









// var id = document.querySelector('#datalist1 option[value=' + g +']').dataset.id;

/*console.log("new option"+id);
    var val = $(id).text();
        console.log("val>>>"+val);
    }

    function det() {
    *//*console.log("val>>>");
      $('#delete-sub-category').on('input change',function() {
        console.log("val>>>22");
        var opt = $(this).find("option:selected").attr("id");

        *//**//*alert(opt.length ? opt.text() : 'NO OPTION');*//**//*
        console.log("id>>>>>"+opt);
      });*//*
      console.log("option id>>>");
      var id;
      var ie;
      $("#delete-sub-category").on('input change', function () {
      var g = $('#delete-sub-category').val();
      id = $('#sub-Category option[value=' + g +']').attr('id');
      console.log("sgdfhdfkds");
      ie=$("#" + id).text();

                                              // var val = $(ie).html();
                                              console.log("option id>>>"+id);
console.log("option valuejhdsgu>>>"+ie);

                                          });
                                          ie=$("#" + id).text();
//                                          *//*console.log("after input change id>>>"+id);
//                                          console.log("option valuejhdsgu>>>"+ie);*//*
    };*/

/*    det();*/





});



function addCategory(categoryName) {
    jsRoutes.controllers.SessionsController.addPrimaryCategory(categoryName).ajax (
        {
            type: 'GET',
            processData: false,
            contentType: false,
            /*beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },*/
            success: function(data) {
                $("#success-message").show();
                $("#wrong-message").hide();
                $("#primary-category").val("");
                document.getElementById("success-message").text(data);
                $("#categories").append("<option value='" + categoryName + "'>" + categoryName + "</option>")
            },
            error: function(er) {
                $("#success-message").hide();
                $("#wrong-message").show();
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
                $("#wrong-message").hide();
                $("#sub-category").val("");
                $("#success-message").show();
                document.getElementById("success-message").innerHTML = data;
                $("#subcategories").append("<option value='" + subCategory + "'>" + categoryName + "</option>");
            },
            error: function(er) {
                $("#wrong-message").show();
                $("#success-message").hide();
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
                $("#wrong-message").hide();
                $("#new-primary-category").val("");
                $("#succes-message").show();
                document.getElementById("success-message").text(data);
            },
            error: function(er) {
                $("#wrong-message").show();
                $("#succes-message").hide();
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
                $("#wrong-message").hide();
                $("#success-message").show();
                $("#new-sub-category").show();
                document.getElementById("success-message").innerHTML = data;
                $("#subcategories").append("<option value='" + newSubCategoryName + "'>" + categoryName + "</option>");
                $("#new-sub-category").val("");
            },
            error: function(er) {
                $("#wrong-message").show();
                $("#succes-message").hide();
                document.getElementById("wrong-message").innerHTML=er.responseText;
                $("#new-sub-category").val("");
             },
        }
    )
}

function deletePrimaryCategory(categoryName){

    jsRoutes.controllers.SessionsController.deletePrimaryCategory(categoryName).ajax(
        {
            type:'GET',
            processData: false,
            contentType: false,

            success: function(data) {
                console.log(data)
                $("#wrong-message").hide();
                $("#succes-message").show();
            },
            error: function(er) {
                console.log("No subcategory is available")
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
                console.log("ggdgdddddddddddd")
            },
            error: function(er) {

            }
        }
    )
}

function topicMatchedWithCategory(categoryName, subCategoryName){
    jsRoutes.controllers.SessionsController.getTopicsBySubCategory(subCategoryName).ajax(
        {
            type:'GET',
            processData: false,
            contentType: 'application/json',
            success: function(data) {
                var topics = JSON.parse(data);
                /*console.log("topics = " + topics);*/
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
                $("#wrong-message").hide();
                $("#success-message").show();
                $("#delete-sub-category").val("");
                document.getElementById("success-message").innerHTML = data;
                $("#subcategory-sessions").hide();
            },
            error: function(er) {
                $("#wrong-message").show();
                $("#succes-message").hide();
                document.getElementById("wrong-message").innerHTML=er.responseText;
                $("#delete-sub-category").val("");
            }
        }
    )
}
