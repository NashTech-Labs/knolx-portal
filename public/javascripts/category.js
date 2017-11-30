function Element(categoryId, subCategory, primaryCategory) {
    this.categoryId = categoryId;
    this.subCategory = subCategory;
    this.primaryCategory = primaryCategory;
}

var fields = [];
var result = "";

$(function () {

    updateDropDown();

    var oldCategoryName = "";
    var oldSubCategoryName = "";
    var subCategoryName = "";
    var categoryName = "";
    var subCategory = "";
    var categoryId = "";
    var modifiedCategoryName = "";

    $("#add-primary-category").click(function (e) {
        categoryName = $("#primary-category").val();
        addCategory(categoryName);
        e.preventDefault();
    });

    $("#search-primary-category").on('input change', function () {
        $("#insert-sub-category").show();
    });

    $("#add-sub-category").click(function () {
        var categoryName = $("#search-primary-category").val();
        subCategory = $("#insert-sub-category").val();
        addSubCategory(categoryName, subCategory);
    });

    $("#modify-primary-category").on('input', function () {
        $("#new-primary-category").show();
        oldCategoryName = $("#modify-primary-category").val();
        modifiedCategoryName = oldCategoryName.replace(" ", "");
        categoryId = $("#" + modifiedCategoryName + "-modify").attr('categoryid');
    });

    $("#modify-primary-category-btn").click(function () {
        $("#modify-primary-category").val("");
        var newCategoryName = $("#new-primary-category").val();
        modifyPrimaryCategory(categoryId, newCategoryName);
    });

    $("#modify-sub-category-btn").click(function () {
        var newSubCategoryName = $("#new-sub-category").val();
        modifySubCategory(categoryId, oldSubCategoryName, newSubCategoryName);
    });

    $("#delete-primary-category").on('input change', function () {
        categoryName = $(this).val();
        if (!(categoryName.trim())) {
            categoryId = "";
            $("#subcategory-linked-category-message").hide();
            $("#no-subCategory").hide();

        } else {
            modifiedCategoryName = categoryName.replace(" ", "");
            categoryId = $("#" + modifiedCategoryName + "-delete").attr('deletecategoryid');
            console.log("CategoryId = " + categoryId);
            subCategoryByPrimaryCategory(categoryName);
        }
    });

    $("#delete-primary-category-btn").click(function () {
        console.log("---------------" + categoryName);
        deletePrimaryCategory(categoryId);
    });

    $("#delete-sub-category-btn").on('click', function () {
        deleteSubCategory(categoryId, subCategoryName);
    });

    $("#mod-sub-category-hover").mouseover(function () {
        showIt('mod-drop-btn');
    });

    $("#mod-sub-category-hover").mouseleave(function () {
        hideIt('mod-drop-btn');
    });

    $("#sub-category-hover").mouseover(function () {
        showIt('drop-btn');
    });

    $("#sub-category-hover").mouseleave(function () {
        hideIt('drop-btn');
    });

    function showIt(id) {
        document.getElementById(id).style.visibility = "visible";
    }

    function hideIt(id) {
        document.getElementById(id).style.visibility = "hidden";
    }

    /* listSubCategoryWithPrimaryCategory();*/

    $("#datalist").keyup(function (e) {
        dropDown('#datalist', '#results-outer', "result");
    });

    $("#mod-datalist").keyup(function (e) {
        dropDown('#mod-datalist', '#mod-results-outer', "mod-result");
    });

    function dropDown(id, targetId, renderResult) {
        console.log(id)
        var keyword = $(id).val().toLowerCase();
        result = "";
        prepareResult(keyword, targetId, renderResult)
    }

    function prepareResult(keyword, targetId, renderResult) {
        console.log("Keyword =" + keyword + " +++ " + targetId);

        if (!keyword) {
            $("#topic-linked-subcategory-message").hide();
            $("#subcategory-sessions").hide();
            $("#no-sessions").hide();
        }
        fields.forEach(function (element) {
            if (element.subCategory.toLowerCase().includes(keyword)) {
                result = result + '<div class="' + renderResult + '" name = "' + element.categoryId + '"id="' + element.subCategory + '-' + element.primaryCategory + '"><div class="sub-category wordwrap"><strong>' + element.subCategory + '</strong></div><div class="primary-category">' + element.primaryCategory + '</div> </div>'
            }
        });
        $(targetId).html(result);
        $(targetId).show();
        result = "";
    }

    $("#drop-btn").click(function (e) {
        showResult("#datalist", "#results-outer", "result");
        e.preventDefault();
    });

    $("#mod-drop-btn").click(function (e) {
        showResult("#mod-datalist", "#mod-results-outer", "mod-result");
        e.preventDefault();
    });

    function showResult(id, targetId, renderResult) {
        var keyword = $(id).val().toLowerCase();
        if (keyword == "") {
            if ($(targetId).is(":visible")) {
                $(targetId).hide();
            } else {
                prepareResult("", targetId, renderResult);
            }
        }
        else {
            if ($(targetId).is(":visible")) {
                $(targetId).hide();
            } else {
                prepareResult(keyword, targetId, renderResult);
            }
        }
    }

    $("#datalist").blur(function () {
        $('#results-outer').hide()
    });

    $(document).mouseup(function (e) {
        var container = $("#holder");
        if (!container.is(e.target) && container.has(e.target).length === 0)
            $('#results-outer').hide()
    });

    $("html").delegate(".result", "mousedown", function () {
        var attribute = $(this).attr('id') + "-" + $(this).attr('name')
        var splits = attribute.split('-');
        console.log("splits = " + splits);
        subCategoryName = splits[0];
        categoryName = splits[1];
        categoryId = splits[2];
        $("#datalist").val(splits[0]);
        topicMatchedWithCategory(categoryName, subCategoryName)
        $("#subcategory-sessions").show();
        $("#pair").val(attribute);
        $('#results-outer').hide();
    });

    var newSubCategoryName = "";
    $("html").delegate(".mod-result", "mousedown", function () {
        var attribute = $(this).attr('id') + "-" + $(this).attr('name');
        var splits = attribute.split('-');
        console.log("splits = " + splits);
        oldSubCategoryName = splits[0];
        categoryName = splits[1];
        categoryId = splits[2];
        $("#mod-datalist").val(splits[0]);
        $("#new-sub-category").show();
        $("#mod-pair").val(attribute);
        $('#mod-results-outer').hide();
    });

    $("html").delegate(".result", "mouseover", function () {
        $(this).addClass('over');
    });

    $("html").delegate(".result", "mouseleave", function () {
        $(this).removeClass('over');
    });

    $("#mod-datalist").blur(function () {
        $('#mod-results-outer').hide()
    });

    $(document).mouseup(function (e) {
        var container = $("#mod-holder");
        if (!container.is(e.target) && container.has(e.target).length === 0)
            $('#mod-results-outer').hide()
    });

    $("html").delegate(".mod-result", "mouseover", function () {
        $(this).addClass('over');
    });

    $("html").delegate(".mod-result", "mouseleave", function () {
        $(this).removeClass('over');
    });

});


/*
function listSubCategoryWithPrimaryCategory() {

    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: 'GET',
            processData: false,
            contentType: 'application/json',
            success: function (values) {
                console.log(values[0].subCategory);
                for (var i = 0; i < values.length; i++) {
                    for (var j = 0; j < values[i].subCategory.length; j++) {

                        var elem = new Element(values[i].categoryId, values[i].subCategory[j], values[i].categoryName);
                        fields.push(elem);
                    }
                }
            }
        });
}*/

function successMessageBox() {
    $("#success-message").show();
    $("#wrong-message").hide();
}

function wrongMessageBox() {
    $("#success-message").hide();
    $("#wrong-message").show();
}

function scrollToTop() {
    $('html, body').animate({scrollTop: 0}, 'fast')
}

function addCategory(categoryName) {

    jsRoutes.controllers.SessionsCategoryController.addPrimaryCategory(categoryName).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function (data) {
                successMessageBox();
                $("#primary-category").val("");
                document.getElementById("disp-success-message").innerHTML = data;
                scrollToTop();
                updateDropDown();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("disp-wrong-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function addSubCategory(categoryName, subCategory) {

    jsRoutes.controllers.SessionsCategoryController.addSubCategory(categoryName, subCategory).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function (data) {
                successMessageBox()
                $("#sub-category").val("");
                document.getElementById("disp-success-message").innerHTML = data;
                updateDropDown();
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("disp-wrong-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function modifyPrimaryCategory(categoryId, newCategoryName) {

    jsRoutes.controllers.SessionsCategoryController.modifyPrimaryCategory(categoryId, newCategoryName).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,

            success: function (data) {
                successMessageBox();
                $("#new-primary-category").val("");
                document.getElementById("disp-success-message").innerHTML = data;
                updateDropDown();
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                $("#new-primary-category").val("");
                document.getElementById("disp-wrong-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function modifySubCategory(categoryId, oldSubCategoryName, newSubCategoryName) {

    jsRoutes.controllers.SessionsCategoryController.modifySubCategory(categoryId, oldSubCategoryName, newSubCategoryName).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function (data) {
                console.log(data);
                successMessageBox();
                $("#new-sub-category").show();
                document.getElementById("disp-success-message").innerHTML = data;
                $("#subcategories").append("<option value='" + newSubCategoryName + "'>" + categoryName + "</option>");
                $("#new-sub-category").val("");
                updateDropDown();
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("disp-wrong-message").innerHTML = er.responseText;
                $("#new-sub-category").val("");
                scrollToTop();
            },
        }
    )
}

function deletePrimaryCategory(categoryId) {

    jsRoutes.controllers.SessionsCategoryController.deletePrimaryCategory(categoryId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: 'application/json',
            success: function (data) {
                successMessageBox();
                $("#delete-primary-category").val("");
                console.log("data is = " + data)
                document.getElementById("disp-success-message").innerHTML = data;
                $("category-sessions").hide();
                updateDropDown();
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                console.log("error is  = " + er.responseText)
                document.getElementById("disp-wrong-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function subCategoryByPrimaryCategory(categoryName) {

    jsRoutes.controllers.SessionsCategoryController.getSubCategoryByPrimaryCategory(categoryName).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: 'application/json',
            success: function (subCategories) {
                $("#no-subCategory").remove();
                if (subCategories.length) {
                    console.log("Sub category = " + subCategories);
                    var subCategoryList = '<ul id="list-sessions">'
                    for (var i = 0; i < subCategories.length; i++) {
                        subCategoryList += '<li ="sub-category-topics">' + subCategories[i] + '</li>';
                    }
                    subCategoryList += '</ul>';
                    $("#subcategory-linked-category-message").show();
                    $("#category-sessions").html(subCategoryList);
                    $("#category-sessions").show();
                }
            },
            error: function (er) {
                $("#no-subCategory").remove();
                var noSubCategory = '<label id="no-subCategory">No sub-category exists</label>'
                $("#category-sessions").before(noSubCategory);
                $("#category-sessions").hide();
            }
        }
    )
}

function topicMatchedWithCategory(categoryName, subCategoryName) {

    jsRoutes.controllers.SessionsCategoryController.getTopicsBySubCategory(categoryName, subCategoryName).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: 'application/json',
            success: function (topics) {
                $("#no-sessions").remove();
                /*var topics = JSON.parse(data);*/
                console.log("topics = " + topics);
                if (topics.length) {
                    var sessions = '<ul id="list-sessions">';
                    for (var i = 0; i < topics.length; i++) {
                        sessions += '<li id="sub-category-topics">' + topics[i] + '</li>';
                    }
                    sessions += "</ul>";
                    $("#topic-linked-subcategory-message").show();
                    $("#subcategory-sessions").html(sessions);
                } else {
                    $("#no-sessions").remove();
                    var noSessions = '<label id= "no-sessions">No sessions exists!</label>'
                    $(".topic-subcategory-message").hide();
                    $("#subcategory-sessions").before(noSessions);
                    $("#subcategory-sessions").hide();
                }
            },
            error: function (er) {
                $("#subcategory-sessions").hide();
            }
        }
    )
}

function deleteSubCategory(categoryId, subCategoryName) {

    jsRoutes.controllers.SessionsCategoryController.deleteSubCategory(categoryId, subCategoryName).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function (data) {
                successMessageBox();
                $("#datalist").val("");
                document.getElementById("disp-success-message").innerHTML = data;
                $("#subcategory-sessions").hide();
                updateDropDown();
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("disp-wrong-message").innerHTML = er.responseText;
                $("#delete-sub-category").val("");
                scrollToTop();
            }
        }
    )
}

function updateDropDown() {
    
    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            success: function (values) {

                console.log(values);
                var categories = "";
                for (var i = 0; i < values.length; i++) {
                    categories += "<option value='" + values[i].categoryName + "'>" + values[i].categoryName + "</option>";
                }
                $("#category-drop-down, #categoryList, #category-list-delete").html(categories);

                fields = [];

                for (var i = 0; i < values.length; i++) {
                    for (var j = 0; j < values[i].subCategory.length; j++) {

                        var elem = new Element(values[i].categoryId, values[i].subCategory[j], values[i].categoryName);
                        fields.push(elem);
                    }
                }
            }
        })
}
