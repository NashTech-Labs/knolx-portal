function Element(categoryId, subCategory, primaryCategory) {
    this.categoryId = categoryId;
    this.subCategory = subCategory;
    this.primaryCategory = primaryCategory;
}

var subCategorySearchResult = [];
var subCategoryOption = "";

$(function () {

    updateDropDown();
    updatePrimaryCategoryDropDown();

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

    $("#delete-primary-category-btn").click(function () {
        deletePrimaryCategory(categoryId);
    });

    $("#add-sub-category").click(function () {
        categoryName = $("#add-sub-category-dropdown").val();
        subCategory = $("#insert-sub-category").val();
        addSubCategory(categoryName, subCategory);
    });

    $("#modify-primary-category-btn").click(function () {
        categoryName = $("#categories-list-modify").val();
        var newCategoryName = $("#modify-primary-category").val();
        modifyPrimaryCategory(categoryName, newCategoryName);
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

    $("#add-sub-category-dropdown").select2({
        ajax: {
            url: "/category/all",
            dataType: "json",
            processResults: function (data, params) {
              var processedData = [];
              for(var i=0 ; i<data.length ; i++) {
                if(params.term == undefined) {
                    processedData.push(data[i].categoryName);
                }
                else if(params.term != "" && data[i].categoryName.toLowerCase.indexOf(params.term) >= 0) {
                  processedData.push(data[i].categoryName);
                  }
                else if(params.term == "") {
                  processedData.push(data[i].categoryName);
                  }
              }
              return {
                results: $.map(processedData, function(obj) {
                    return  { id: obj, text: obj };
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
              for(var i=0 ; i<data.length ; i++) {
                if(params.term == undefined) {
                    processedData.push(data[i]);
                }
                else if(params.term != "" && data[i].categoryName.toLowerCase.indexOf(params.term) >= 0) {
                  processedData.push(data[i]);
                  }
                else if(params.term == "") {
                  processedData.push(data[i]);
                  }
              }
              return {
                results: $.map(processedData, function(obj) {
                    return  { id: obj.categoryId, text: obj.categoryName };
                })
              };
            }
        },
        containerCssClass: "category-select2",
        placeholder: "Select/Search a category"
    });

    $("#delete-primary-category").select2({
        ajax: {
            url: "/category/all",
            dataType: "json",
            processResults: function (data, params) {
              var processedData = [];
              for(var i=0 ; i<data.length ; i++) {
                if(params.term == undefined) {
                    processedData.push(data[i]);
                }
                else if(params.term != "" && data[i].categoryName.toLowerCase.indexOf(params.term) >= 0) {
                  processedData.push(data[i]);
                  }
                else if(params.term == "") {
                  processedData.push(data[i]);
                  }
              }
              return {
                results: $.map(processedData, function(obj) {
                    return  { id: obj.categoryId, text: obj.categoryName };
                })
              };
            }
        },
        containerCssClass: "category-select2",
        placeholder: "Select/Search a category"
    });

    $('#add-sub-category-dropdown').on("select2:selecting", function(e) {
        console.log("Something has been selected");
       $("#insert-sub-category").show();
    });

    $('#categories-list-modify').on("select2:selecting", function(e) {
        console.log("Something has been selected");
       $("#modify-primary-category").show();
    });

    $('#delete-primary-category').on("change", function(e) {
        categoryId = $(this).val();
        categoryName = $("option[value='"+ categoryId +"']").html();
        console.log("Primary category " + categoryName + "has been selected");
        console.log("categoryId = " + categoryId);
       subCategoryByPrimaryCategory(categoryName)
    });

    function showIt(id) {
        document.getElementById(id).style.visibility = "visible";
    }

    function hideIt(id) {
        document.getElementById(id).style.visibility = "hidden";
    }

    $("#datalist").keyup(function (e) {
        subCategoryDropDown('#datalist', '#results-outer', "result");
    });

    $("#search-primary-category-add").keyup(function (e) {
        subCategoryDropDown('#search-primary-category-add', '#primary-options-outer', "result");
    });

    $("#mod-datalist").keyup(function (e) {
        subCategoryDropDown('#mod-datalist', '#mod-results-outer', "mod-result");
    });

    function subCategoryDropDown(id, targetId, renderResult) {
        console.log(id)
        var keyword = $(id).val().toLowerCase();
        console.log("Keyword = " + keyword);
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
                subCategoryOption = subCategoryOption + '<div class="' + renderResult + '" name = "' + element.subCategory + '"id="' + element.categoryId + '" categoryValue ="' + element.primaryCategory + '"><div class="sub-category wordwrap"><strong>' + element.subCategory + '</strong></div><div class="primary-category">' + element.primaryCategory + '</div> </div>'
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
        categoryId = $(this).attr('id')
        subCategoryName = $(this).attr('name')
        categoryName = $(this).attr('categoryValue')
        topicBySubCategory(categoryName, subCategoryName)
        $("#datalist").val(subCategoryName);
        $("#subcategory-sessions").show();
        $("#hidden-sub-category").val(subCategoryName);
        $('#results-outer').hide();
    });

    $("html").delegate(".mod-result", "mousedown", function () {
        categoryId = $(this).attr('id')
        oldSubCategoryName = $(this).attr('name')
        $("#mod-datalist").val(oldSubCategoryName);
        $("#new-sub-category").show();
        $("#old-sub-category").val(oldCategoryName);
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

    /*for primary category search*/

    $("#search-primary-category-add").click(function () {
        primaryCategoryDropDown('#search-primary-category-add', '#primary-options-outer', "result");
    });

    function primaryCategoryDropDown(id, targetId, renderResult) {
        console.log(id)
        var keyword = $(id).val().toLowerCase();
        subCategoryOption = "";
        searchPrimaryCategory(keyword, targetId, renderResult)
    }

    function searchPrimaryCategory(keyword, targetId, renderResult){

        categorySearchResult.forEach(function (element) {
           if(element.primaryCategory.toLowerCase().includes(keyword)){
              categoryOption = categoryOption + '<div class="result" id="'+ element.categoryId +'"><div class="primary-category wordwrap"><strong>'+element.primaryCategory+'</strong></div></div>'
           }
        });
        $(targetId).html(categoryOption);
        $(targetId).show();
        categoryOption = "";
    }




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
                successMessageBox();
                $("#primary-category").val("");
                document.getElementById("display-success-message").innerHTML = data;
                scrollToTop();
                updateDropDown();
            },
            error: function (er) {
                failureMessageBox();
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                scrollToTop();
            }
        }
    )
}

function addSubCategory(categoryName, subCategory) {
console.log("Sending primary category = " + categoryName + "sub category = " + subCategory);

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
                failureMessageBox();
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
                failureMessageBox();
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
                failureMessageBox();
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
                failureMessageBox();
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
                    $("#subcategory-linked-category-message").hide();
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
                document.getElementById("display-failure-message").innerHTML = er.responseText;
                $("#delete-sub-category").val("");
                failureMessageBox();
                scrollToTop();
            }
        }
    )
}

function updatePrimaryCategoryDropDown() {

    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            success: function (values) {
                var categories = "";
                console.log(values);

                categorySearchResult = [];

                for (var i = 0; i < values.length; i++) {
                    var option = new primaryElement(values[i].categoryId, values[i].categoryName);
                    categorySearchResult.push(option);
                }
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