function Element(categoryId, subCategory, primaryCategory) {
    this.categoryId = categoryId;
    this.subCategory = subCategory;
    this.primaryCategory = primaryCategory;
}

var subCategorySearchResult = [];
var subCategoryOption = "";
var searchCategoryPlaceholder = "<span class='select2-selection__placeholder'>Select/Search a category</span>";

$(function () {

    updateSubCategoryDropDown();

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

    $('#categories-list-modify').on("change", function (e) {
        $("#new-primary-category").show();
    });

    $("#modify-primary-category-btn").click(function () {
        categoryName = $("#categories-list-modify").val();
        var newCategoryName = $("#new-primary-category").val();
        modifyPrimaryCategory(categoryName, newCategoryName);
    });

    $('#categories-list-delete').on("change", function (e) {
        categoryId = $(this).val();
        categoryName = $("option[value='" + categoryId + "']").html();
        subCategoryByPrimaryCategory(categoryName);
    });

    $("#delete-primary-category-btn").click(function () {
        deletePrimaryCategory(categoryId);
    });

    $('#categories-list-add').on("change", function (e) {
        $("#insert-sub-category").show();
    });

    $("#add-sub-category-btn").click(function () {
        categoryName = $("#categories-list-add").val();
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

    $("#categories-list-add").select2({
        ajax: {
            url: "/category/all",
            dataType: "json",
            processResults: function (data, params) {
                var processedData = [];
                for (var i = 0; i < data.length; i++) {
                    if (params.term == undefined) {
                        processedData.push(data[i]);
                    } else if (params.term != "" && (data[i].categoryName.toLowerCase().indexOf(params.term.toLowerCase())) >= 0) {
                        processedData.push(data[i]);
                    } else if (params.term == "") {
                        processedData.push(data[i]);
                    }
                }

                return {
                    results: $.map(processedData, function (obj) {
                        return {id: obj.categoryId, text: obj.categoryName};
                    })
                };
            }
        },
        containerCssClass: "category-select2",
        placeholder: "Select/Search a category"
    });

    $("#categories-list-modify").select2({
        ajax: {
            url: "/category/all",
            dataType: "json",
            processResults: function (data, params) {
                var processedData = [];
                for (var i = 0; i < data.length; i++) {
                    if (params.term == undefined) {
                        processedData.push(data[i]);
                    } else if (params.term != "" && (data[i].categoryName.toLowerCase().indexOf(params.term.toLowerCase())) >= 0) {
                        processedData.push(data[i]);
                    } else if (params.term == "") {
                        processedData.push(data[i]);
                    }
                }

                return {
                    results: $.map(processedData, function (obj) {
                        return {id: obj.categoryId, text: obj.categoryName};
                    })
                };
            }
        },
        containerCssClass: "category-select2",
        placeholder: "Select/Search a category",
        allowClear: true
    });

    $("#categories-list-delete").select2({
        ajax: {
            url: "/category/all",
            dataType: "json",
            processResults: function (data, params) {
                var processedData = [];
                for (var i = 0; i < data.length; i++) {
                    if (params.term == undefined) {
                        processedData.push(data[i]);
                    } else if (params.term != "" && (data[i].categoryName.toLowerCase().indexOf(params.term.toLowerCase())) >= 0) {
                        processedData.push(data[i]);
                    } else if (params.term == "") {
                        processedData.push(data[i]);
                    }
                }

                return {
                    results: $.map(processedData, function (obj) {
                        return {id: obj.categoryId, text: obj.categoryName};
                    })
                };
            }
        },
        containerCssClass: "category-select2",
        placeholder: "Select/Search a category"
    });

    $("#sub-categories-list-delete").keyup(function (e) {
        subCategoryDropDown('#sub-categories-list-delete', '#sub-categories-container-result-delete', "sub-categories-row-delete");
    });

    $("#sub-categories-list-modify").keyup(function (e) {
        subCategoryDropDown('#sub-categories-list-modify', '#sub-categories-container-result-modify', "sub-categories-row-modify");
    });

    function subCategoryDropDown(id, targetId, renderResult) {
        var keyword = $(id).val().toLowerCase();
        updateSubCategoryDropDown();
        subCategoryOption = "";
        prepareResult(keyword, targetId, renderResult)
    }

    function prepareResult(keyword, targetId, renderResult) {
        if (!keyword) {
            $("#topic-linked-subcategory-message").hide();
            $("#topics-exists").hide();
            $("#no-sessions").hide();
            $("#no-subCategory").hide();
        }

        subCategorySearchResult.forEach(function (element) {
            if (element.subCategory.toLowerCase().includes(keyword)) {
                subCategoryOption = subCategoryOption + '<div class="' + renderResult + '" name = "' + element.subCategory + '"id="' + element.categoryId + '" categoryValue ="' + element.primaryCategory + '"><div class="sub-category wordwrap"><strong>' + element.subCategory + '</strong></div><div class="primary-category">' + element.primaryCategory + '</div> </div>'
            }
        });

        $(targetId).html(subCategoryOption);
        $(targetId).show();
        var width = $("#sub-categories-container-modify").width();
        $(targetId).css('width', width);

        subCategoryOption = "";
    }

    $("#drop-down-btn-delete").click(function (e) {
        showResult("#sub-categories-list-delete", "#sub-categories-container-result-delete", "sub-categories-row-delete");
        e.preventDefault();
    });

    $("#drop-down-btn-modify").click(function (e) {
        showResult("#sub-categories-list-modify", "#sub-categories-container-result-modify", "sub-categories-row-modify");
        e.preventDefault();
    });

    function showResult(id, targetId, renderResult) {
        var keyword = $(id).val().toLowerCase();
        updateSubCategoryDropDown();
        console.log(">>>>>>>>>>>>>>>>>>>>>This should be printed after data is returned");
        if (keyword == "") {
            if ($(targetId).is(":visible")) {
                $(targetId).hide();
            } else {
                prepareResult("", targetId, renderResult);
            }
        } else {
            if ($(targetId).is(":visible")) {
                $(targetId).hide();
            } else {
                prepareResult(keyword, targetId, renderResult);
            }
        }
    }

    $("#sub-categories-list-delete").blur(function () {
        $('#sub-categories-container-result-delete').hide()
    });

    $(document).mouseup(function (e) {
        var container = $("#sub-categories-container-delete");
        if (!container.is(e.target) && container.has(e.target).length === 0)
            $('#sub-categories-container-result-delete').hide()
    });

    $("html").delegate(".sub-categories-row-delete", "mousedown", function () {
        categoryId = $(this).attr('id');
        subCategoryName = $(this).attr('name');
        categoryName = $(this).attr('categoryValue');
        topicBySubCategory(categoryName, subCategoryName);
        $("#sub-categories-list-delete").val(subCategoryName);
        $("#topics-exists").show();
        $("#hidden-sub-category").val(subCategoryName);
        $('#sub-categories-container-result-delete').hide();
    });

    $("html").delegate(".sub-categories-row-modify", "mousedown", function () {
        categoryId = $(this).attr('id');
        oldSubCategoryName = $(this).attr('name');
        $("#sub-categories-list-modify").val(oldSubCategoryName);
        $("#new-sub-category").show();
        $("#old-sub-category").val(oldCategoryName);
        $('#sub-categories-container-result-modify').hide();
    });

    $("html").delegate(".sub-categories-row-delete", "mouseover", function () {
        $(this).addClass('hover');
    });

    $("html").delegate(".sub-categories-row-delete", "mouseleave", function () {
        $(this).removeClass('hover');
    });

    $("html").delegate(".sub-categories-row-modify", "mouseover", function () {
        $(this).addClass('hover');
    });

    $("html").delegate(".sub-categories-row-modify", "mouseleave", function () {
        $(this).removeClass('hover');
    });

    $("#sub-categories-list-modify").blur(function () {
        $('#sub-categories-container-result-modify').hide()
    });

    $(document).mouseup(function (e) {
        var container = $("#sub-categories-container-modify");
        if (!container.is(e.target) && container.has(e.target).length === 0)
            $('#sub-categories-container-result-modify').hide()
    });

});

function successMessageBox() {
    $("#success-message").show();
    $("#failure-message").hide();
}

function failureMessageBox() {
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
                $("#primary-category").val("");
                document.getElementById("display-success-message").innerHTML = data;
                successMessageBox();
                scrollToTop();
            },
            error: function (er) {
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                failureMessageBox();
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
                $("#select2-categories-list-add-container").html(searchCategoryPlaceholder);
                $("#insert-sub-category").val("");
                $("#categories-list-add").val("");
                document.getElementById("display-success-message").innerHTML = data;
                updateSubCategoryDropDown();
                successMessageBox();
                scrollToTop();
            },
            error: function (er) {
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                failureMessageBox();
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
                $("#select2-categories-list-modify-container").html(searchCategoryPlaceholder);
                $("#new-primary-category").val("");
                document.getElementById("display-success-message").innerHTML = data;
                successMessageBox();
                scrollToTop();
            },
            error: function (er) {
                $("#new-primary-category").val("");
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                failureMessageBox();
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
                $("#new-sub-category").show();
                document.getElementById("display-success-message").innerHTML = data;
                $("#new-sub-category").val("");
                updateSubCategoryDropDown();
                successMessageBox();
                scrollToTop();
            },
            error: function (er) {
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                $("#new-sub-category").val("");
                failureMessageBox();
                scrollToTop();
            }
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
                $("#select2-categories-list-delete-container").html(searchCategoryPlaceholder);
                $("#categories-list-delete").val("");
                document.getElementById("display-success-message").innerHTML = data;
                $("#sub-categories-exists").hide();
                $("#no-subCategory").hide();
                successMessageBox();
                scrollToTop();
            },
            error: function (er) {
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                failureMessageBox();
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
                    var subCategoryList = '<ul id="list-sessions">';
                    for (var i = 0; i < subCategories.length; i++) {
                        subCategoryList += '<li ="sub-category-topics">' + subCategories[i] + '</li>';
                    }
                    subCategoryList += '</ul>';
                    $("#subcategory-linked-category-message").show();
                    $("#sub-categories-exists").html(subCategoryList);
                    $("#sub-categories-exists").show();
                } else {
                    $("#no-subCategory").remove();
                    var noSubCategory = '<label id="no-subCategory">No sub-category exists</label>';
                    $("#sub-categories-exists").before(noSubCategory);
                    $("#sub-categories-exists").hide();
                    $("#subcategory-linked-category-message").hide();
                }

            },
            error: function (er) {
                $("#sub-categories-exists").hide();
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
                    $("#topics-exists").html(sessions);
                } else {
                    $("#no-sessions").remove();
                    var noSessions = '<label id= "no-sessions">No sessions exists!</label>';
                    $(".topic-subcategory-message").hide();
                    $("#topics-exists").before(noSessions);
                    $("#topics-exists").hide();
                }
            },
            error: function (er) {
                $("#topics-exists").hide();
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
                $("#sub-categories-list-delete").val("");
                document.getElementById("display-success-message").innerHTML = data;
                $("#topics-exists").hide();
                $("#no-sessions").hide();
                updateSubCategoryDropDown();
                successMessageBox();
                scrollToTop();
            },
            error: function (er) {
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                $("#delete-sub-category").val("");
                failureMessageBox();
                scrollToTop();
            }
        }
    )
}

function updateSubCategoryDropDown() {

    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            async: false,
            success: function (values) {
                subCategorySearchResult = [];
                console.log("Values >>>>>>>>>>>>>>>>>>>>>>" + JSON.stringify(values))

                for (var i = 0; i < values.length; i++) {
                    for (var j = 0; j < values[i].subCategory.length; j++) {
                        var option = new Element(values[i].categoryId, values[i].subCategory[j], values[i].categoryName);
                        subCategorySearchResult.push(option);
                    }
                }

            }
        })
}
