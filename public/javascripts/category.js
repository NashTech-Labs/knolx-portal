$(function () {

    var oldCategoryName = "";
    var oldSubCategoryName = "";
    $("#add-primary-category").click( function () {
        var categoryName = $("#primary-category").val();
        addCategory(categoryName);
    });

    var subCategory = "";
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

    $("#delete-primary-category-btn").click( function() {
        $("#delete-sub-category").on('input change', function () {
            categoryName=$(this).val();
            console.log("categoryName");
        })
    })


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
                $("#succes-message").show();
                document.getElementById("success-message").text(data);
                $("#subcategories").append("<option value='" + subCategory + "'>" + categoryName + "</option>");
            },
            error: function(er) {
                $("#wrong-message").show();
                $("#succes-message").hide();
                document.getElementById("wrong-message").innerHTML=er.responseText;
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
                $("#wrong-message").hide();
                $("#succes-message").show();
                $("#new-sub-category").show();
                document.getElementById("success-message").text(data);
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
