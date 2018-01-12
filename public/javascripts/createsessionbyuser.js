$(document).ready(function () {

    $('#category, #subCategory').select2();
    $('#select-date').select2();

    jsRoutes.controllers.SessionsCategoryController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            success: function (values) {
                var categories = "";
                var primaryCategory = $("#primary-category").val();
                var subCategory = $("#sub-category").val();
                for (var i = 0; i < values.length; i++) {
                    categories += "<option value='" + values[i].categoryName + "'";
                    if (values[i].categoryName === primaryCategory) {
                        categories += "selected";
                    }
                    categories += ">" + values[i].categoryName + "</option>";
                }
                $("#category").append(categories);
                showSubCategory(primaryCategory, subCategory, values);

                $("select#category").on('change', function () {
                    var selected = $('#category option:selected').val();
                    for (var i = 0; i < values.length; i++) {
                        if (selected == values[i].categoryName) {
                            var subCategories = "";
                            for (var j = 0; j < values[i].subCategory.length; j++) {
                                subCategories += "<option value='" + values[i].subCategory[j] + "'>" + values[i].subCategory[j] + "</option>";
                            }
                            $("#subCategory").html(subCategories);
                            break;
                        } else {
                            var subCategories = "";
                            subCategories += "<option value=''>! Select Sub Category Please</option>";
                            $("#subCategory").html(subCategories);
                        }
                    }
                });
            }
        });

    onFormSubmit();
});

function showSubCategory(primaryCategory, subCategory, values) {
    for (var i = 0; i < values.length; i++) {
        if (primaryCategory === values[i].categoryName) {
            var subCategories = "";
            for (var j = 0; j < values[i].subCategory.length; j++) {
                subCategories += "<option value='" + values[i].subCategory[j] + "'";
                if (subCategory === values[i].subCategory[j]) {
                    subCategories += "selected";
                }
                subCategories += ">" + values[i].subCategory[j] + "</option>";
            }
            $("#subCategory").html(subCategories);
            break;
        } else {
            var subCategories = "";
            subCategories += "<option value=''>! Select Sub Category Please</option>";
            $("#subCategory").html(subCategories);
        }
    }
}

function onFormSubmit() {
    $('#create-session-form').submit( function () {
        var selectedId = $("#select-date option:selected").attr('id');
        console.log("ID of a free slot ---> " + selectedId);
        $('<input />').attr('name', 'freeSlotId')
            .attr('id', "freeSlotId")
            .attr('value', selectedId)
            .attr('type', 'hidden')
            .appendTo(this);
    });
}
