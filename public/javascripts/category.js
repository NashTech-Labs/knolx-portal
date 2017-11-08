$(function () {
    var oldCategoryName;
    var oldSubCategoryName;
    $("#add-primary-category").click( function () {
        var categoryName = $("#primary-category").val();
        addCategory(categoryName);
    });

    $("#add-sub-category").click( function(){
            var categoryName = $("#search-primary-category").val();
            var subCategory = $("#sub-category").val();

            console.log(categoryName,subCategory);
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
    });

    $("#memory").click( function() {

       var newSubCategoryName = $("#new-sub-category").val();
        var oldSubCategoryName = $("#modify-sub-category").val();
        var categoryName =  $('#modify-sub-category').html;

        alert(newSubCategoryName + " " + oldSubCategoryName + " " + categoryName)
        modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName);

    });


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
                $("#successful-add-category").show();
                $("#categories").append("<option value='" + categoryName + "'>" + categoryName + "</option>")
                $("#primary-category").val("");
            },
            error: function(er) {
                $("#unsuccessful-add-category").show();
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
                $("#successful-add-sub-category").show();
                $("#search-primary-secondary").val("");

                $("#sub-category").val("");
            },
            error: function(er) {
                $("#unsuccessful-add-sub-category").show();
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
                $("#successful-modify-primary-category").show();
                $("#new-primary-category").val("");

            },
            error: function(er) {
                $("#unsuccessful-modify-primary-category").show();
            }
        }
    )
}
/*

function modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName){

    jsRoutes.controllers.SessionsController.modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName).ajax (
        {
            type:'GET',
            processData: false,
            contentType: false,

            success: function(data) {
                $("#successful-modify-sub-category").show();
                $("#new-sub-category").val("");
            },
            error: function(er) {
                $("#unsuccessful-modify-sub-category").show();
            }
        }
    )
}

*/

function modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName){

    jsRoutes.controllers.SessionsController.modifySubCategory(categoryName, oldSubCategoryName, newSubCategoryName).ajax(
        {
            type:'GET',
            processData: false,
            contentType: 'application/json',

            success: function(data) {
                var values = JSON.parse(data);
                console.log(values);
                $("#successful-modify-sub-category").show();
                $("#new-sub-category").val("");


            },
            error: function(er) {
                console.log("No subcategory is available")
            }
        }
    )

}