$(document).ready(function () {

    jsRoutes.controllers.SessionsController.getCategory().ajax(
        {
            type: "GET",
            processData: false,
            success: function (data) {
                console.log(data);
                var values = JSON.parse(data);
                var categories = "";
                for (var i = 0; i < values.length; i++) {
                    categories += "<option value='" + values[i].categoryName + "'>" + values[i].categoryName + "</option>";
                }
                $("#category").append(categories);
                $("select#category").on('change', function () {
                    var selected = $('#category option:selected').val();
                    for (var i = 0; i < values.length; i++) {
                        if (selected == values[i].categoryName) {
                            var subCategories = "";
                            for (var j = 0; j < values[i].subCategory.length; j++) {
                                subCategories += "<option value='" + values[i].subCategory[j] + "'>" + values[i].subCategory[j] + "</option>";
                            }
                            $("#subCategory").append(subCategories);
                            break;
                        } else {
                            var subCategories = "";
                            subCategories += "<option value=''>! Select Sub Category Please</option>";
                            $("#subCategory").html(subCategories);
                        }
                    }
                });
            }
        })
});
