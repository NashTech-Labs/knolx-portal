function Element(categoryId, subCategory, primaryCategory) {
    this.categoryId = categoryId;
    this.subCategory = subCategory;
    this.primaryCategory = primaryCategory;
}

var subCategorySearchResult = [];
var subCategoryOption = "";

$(function () {

    $('#search-primary-category').select2({
        dropdownCssClass: "akshansh"
    });

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

    console.log(">>>>>>>>>>>>>>  >>>>>>");
    $(".akshansh").click(function () {
        console.log("Search primary category");
        updateAddPrimaryCategoryDropDown();
        $("#insert-sub-category").show();
    });

    $("#modify-primary-category").on('focus', function () {
        updateModifyPrimaryCategoryDropDown();
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

    $("#delete-primary-category").on('input change', function () {
        updateDeletePrimaryCategoryDropDown();
        categoryName = $(this).val();
        if (!(categoryName.trim())) {
            categoryId = "";
            $("#subcategory-linked-category-message").hide();
            $("#category-sessions").hide();
        } else {
            modifiedCategoryName = categoryName.replace(" ", "");
            categoryId = $("#" + modifiedCategoryName + "-delete").attr('deletecategoryid');
            subCategoryByPrimaryCategory(categoryName);
        }
    });

    $("#delete-primary-category-btn").click(function () {
        deletePrimaryCategory(categoryId);
    });

    $("#add-sub-category").click(function () {
        var categoryName = $("#search-primary-category").val();
        subCategory = $("#insert-sub-category").val();
        addSubCategory(categoryName, subCategory);
    });

    $("#modify-sub-category-btn").click(function () {
        var newSubCategoryName = $("#new-sub-category").val();
        modifySubCategory(categoryId, oldSubCategoryName, newSubCategoryName);
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

    $("#datalist").keyup(function (e) {
        dropDown('#datalist', '#results-outer', "result");
    });

    $("#mod-datalist").keyup(function (e) {
        dropDown('#mod-datalist', '#mod-results-outer', "mod-result");
    });

    function dropDown(id, targetId, renderResult) {
        console.log(id)
        var keyword = $(id).val().toLowerCase();
        subCategoryOption = "";
        prepareResult(keyword, targetId, renderResult)
    }

    function prepareResult(keyword, targetId, renderResult) {

        if (!keyword) {
            $("#topic-linked-subcategory-message").hide();
            $("#subcategory-sessions").hide();
            $("#no-sessions").hide();
            $("#no-subCategory").hide();

        }
        subCategorySearchResult.forEach(function (element) {
            if (element.subCategory.toLowerCase().includes(keyword)) {
                subCategoryOption = subCategoryOption + '<div class="' + renderResult + '" name = "' + element.categoryId + '"id="' + element.subCategory + '-' + element.primaryCategory + '"><div class="sub-category wordwrap"><strong>' + element.subCategory + '</strong></div><div class="primary-category">' + element.primaryCategory + '</div> </div>'
            }
        });
        $(targetId).html(subCategoryOption);
        $(targetId).show();
        subCategoryOption = "";
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
        subCategoryName = splits[0];
        categoryName = splits[1];
        categoryId = splits[2];
        $("#datalist").val(subCategoryName);
        topicBySubCategory(categoryName, subCategoryName)
        $("#subcategory-sessions").show();
        $("#pair").val(attribute);
        $('#results-outer').hide();
    });

    $("html").delegate(".mod-result", "mousedown", function () {
        var attribute = $(this).attr('id') + "-" + $(this).attr('name');
        var splits = attribute.split('-');
        oldSubCategoryName = splits[0];
        categoryName = splits[1];
        categoryId = splits[2];
        $("#mod-datalist").val(oldSubCategoryName);
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

function successMessageBox() {
    $("#success-message").show();
    $("#failure-message").hide();
}

function wrongMessageBox() {
    $("#success-message").hide();
    $("#failure-message").show();
}

function scrollToTop() {
    $('html, body').animate({scrollTop: 0}, 'fast')
}

function addCategory(categoryName) {

    jsRoutes.controllers.SessionsCategoryController.addPrimaryCategory(categoryName).ajax(
        {
            type: 'PUT',
            processData: false,
            contentType: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                successMessageBox();
                $("#primary-category").val("");
                document.getElementById("display-success-message").innerHTML = data;
                scrollToTop();
                updateDropDown();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function addSubCategory(categoryName, subCategory) {

    jsRoutes.controllers.SessionsCategoryController.addSubCategory(categoryName, subCategory).ajax(
        {
            type: 'PUT',
            processData: false,
            contentType: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                successMessageBox()
                $("#sub-category").val("");
                document.getElementById("display-success-message").innerHTML = data;
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function modifyPrimaryCategory(categoryId, newCategoryName) {

    jsRoutes.controllers.SessionsCategoryController.modifyPrimaryCategory(categoryId, newCategoryName).ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                successMessageBox();
                $("#new-primary-category").val("");
                document.getElementById("display-success-message").innerHTML = data;
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                $("#new-primary-category").val("");
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function modifySubCategory(categoryId, oldSubCategoryName, newSubCategoryName) {

    jsRoutes.controllers.SessionsCategoryController.modifySubCategory(categoryId, oldSubCategoryName, newSubCategoryName).ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                console.log(data);
                successMessageBox();
                $("#new-sub-category").show();
                document.getElementById("display-success-message").innerHTML = data;
                $("#new-sub-category").val("");
                scrollToTop();
                updateDropDown();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                $("#new-sub-category").val("");
                scrollToTop();
            },
        }
    )
}

function deletePrimaryCategory(categoryId) {

    jsRoutes.controllers.SessionsCategoryController.deletePrimaryCategory(categoryId).ajax(
        {
            type: 'DELETE',
            processData: false,
            contentType: 'application/json',
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                successMessageBox();
                $("#delete-primary-category").val("");
                document.getElementById("display-success-message").innerHTML = data;
                $("category-sessions").hide();
                $("#no-subCategory").hide();
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("display-failure-message").innerHTML = er.responseText;
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
                } else {
                    $("#no-subCategory").remove();
                    var noSubCategory = '<label id="no-subCategory">No sub-category exists</label>'
                    $("#category-sessions").before(noSubCategory);
                    $("#category-sessions").hide();
                }

            },
            error: function (er) {
                $("#category-sessions").hide();
            }
        }
    )
}

function topicBySubCategory(categoryName, subCategoryName) {

    jsRoutes.controllers.SessionsCategoryController.getTopicsBySubCategory(categoryName, subCategoryName).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: 'application/json',
            success: function (topics) {
                $("#no-sessions").remove();
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
            type: 'DELETE',
            processData: false,
            contentType: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                successMessageBox();
                $("#datalist").val("");
                document.getElementById("display-success-message").innerHTML = data;
                $("#subcategory-sessions").hide();
                $("#no-sessions").hide();
                updateDropDown();
                scrollToTop();
            },
            error: function (er) {
                wrongMessageBox();
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                $("#delete-sub-category").val("");
                scrollToTop();
            }
        }
    )
}

function updateAddPrimaryCategoryDropDown() {

    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            success: function (values) {
                var categories = "";
                console.log(values);
                var primaryCategory = $("#primary-category").val();
                for (var i = 0; i < values.length; i++) {
                    console.log("........");
                    categories += "<option value='" + values[i].categoryId + "'";
                    if (values[i].categoryName === primaryCategory) {
                        categories += "selected";
                    }
                    categories+=    ">" + values[i].categoryName + "</option>";
                }
                alert("categories list >>> " + categories);
                $("#search-primary-category").html(categories);
            }
        }
    )
}

function updateModifyPrimaryCategoryDropDown() {

    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            success: function (values) {

                var modifyCategoryList = "";
                for (var i = 0; i < values.length; i++) {
                    modifyCategoryList += "<option id='" + values[i].categoryName.replace(' ', '') + "-modify' categoryid='" +
                        values[i].categoryId + "'value='" + values[i].categoryName + "'></option>";
                }
                $("#categories-list-modify").html(modifyCategoryList);
            }
        }
    )
}

function updateDeletePrimaryCategoryDropDown() {

    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            success: function (values) {

                var deleteCategoryList = "";
                for (var i = 0; i < values.length; i++) {
                    deleteCategoryList += "<option id='" + values[i].categoryName.replace(' ', '') + "-delete' deletecategoryid='" +
                        values[i].categoryId + "'value='" + values[i].categoryName + "'></option>";
                }
                $("#categories-list-delete").html(deleteCategoryList);
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

                var categories = "";
                var categoriesModify = "";
                var categoriesDelete = "";

                for (var i = 0; i < values.length; i++) {
                    categories += "<option value='" + values[i].categoryName + "'>" + values[i].categoryName + "</option>";
                    categoriesModify += "<option id='" + values[i].categoryName.replace(' ', '') + "-modify' categoryid='" +
                        values[i].categoryId + "'value='" + values[i].categoryName + "'></option>";

                    categoriesDelete += "<option id='" + values[i].categoryName.replace(' ', '') + "-delete' deletecategoryid='" +
                        values[i].categoryId + "'value='" + values[i].categoryName + "'></option>";
                }

                subCategorySearchResult = [];

                for (var i = 0; i < values.length; i++) {
                    for (var j = 0; j < values[i].subCategory.length; j++) {

                        var option = new Element(values[i].categoryId, values[i].subCategory[j], values[i].categoryName);
                        subCategorySearchResult.push(option);
                    }
                }
            }
        })
}
