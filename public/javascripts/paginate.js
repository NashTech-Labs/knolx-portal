function paginate(pageNumber, pages) {

    var pagination = "";
    if (pages > 5 && pageNumber < 5) {
        for (var iterator = 1; iterator <= 5; iterator++) {
            if (pageNumber == iterator) {
                pagination += "<li class='active'><a  class='paginate' id='" + iterator + "'>" + iterator + "</a></li>";
            }
            else {
                pagination += "<li class='inactive'><a  class='paginate' id='" + iterator + "'>" + iterator + "</a></li>";
            }

        }
        pagination += "<li class='inactive'><a>...</a></li>";
        pagination += "<li class='inactive'><a  class='paginate' id='" + pages + "'>" + pages + "</a></li>";

    }

    if (pages <= 5 && pageNumber <= 5) {
        for (iterator = 1; iterator <= pages; iterator++) {
            if (pageNumber == iterator) {
                pagination += "<li class='active'><a class='paginate' id='" + iterator + "'>" + iterator + "</a></li>";
            }
            else {
                pagination += "<li class='inactive'><a  class='paginate'  id='" + iterator + "'>" + iterator + "</a></li>";
            }
        }
    }

    if (pages > 5 && pageNumber >= 5 && pages == pageNumber) {
        for (iterator = 5; iterator >= 1; iterator--) {
            if (pageNumber == pages - iterator) {
                pagination += "<li class='active'><a  class='paginate' id='" + (pages - iterator) + "'>" + (pages - iterator) + "</a></li>";
            }
            else {
                pagination += "<li class='inactive'><a  class='paginate' id='" + (pages - iterator) + "'>" + (pages - iterator) + "</a></li>";
            }
        }
        pagination += "<li><a>...</a></li>";

        if (pageNumber == pages) {
            pagination += "<li class='active'><a  class='paginate' id='" + pages + "'>" + pages + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + pages + "'>" + pages + "</a></li>";
        }
    }

    if (pages > 5 && pageNumber >= 5 && pageNumber < pages - 2) {

        if (pageNumber == pageNumber - 2) {
            pagination += "<li class='active'><a  class='paginate' id='" + (pageNumber - 2) + "'>" + (pageNumber - 2) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + ( pageNumber - 2) + "'>" + (pageNumber - 2) + "</a></li>";
        }

        if (pageNumber == pageNumber - 1) {
            pagination += "<li class='active'><a  class='paginate' id='" + (pageNumber - 1) + "'>" + (pageNumber - 1) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + (pageNumber - 1) + "'>" + (pageNumber - 1) + "</a></li>";
        }
        if (pageNumber == pageNumber) {
            pagination += "<li class='active'><a  class='paginate' id='" + pageNumber + "'>" + pageNumber + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a class='paginate'  id='" + pageNumber + "'>" + pageNumber + "</a></li>";
        }
        if (pageNumber == pageNumber + 1) {
            pagination += "<li class='active'><a  class='paginate' id='" + (pageNumber + 1) + "'>" + (pageNumber + 1) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + (pageNumber + 1) + "'>" + (pageNumber + 1) + "</a></li>";
        }
        if (pageNumber == pageNumber + 2) {
            pagination += "<li class='active'><a class='paginate'  id='" + (pageNumber + 2) + "'>" + (pageNumber + 2) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + ( pageNumber + 2) + "'>" + (pageNumber + 2) + "</a></li>";
        }

        pagination += "<li class='inactive'><a>...</a></li>";
        pagination += "<li class='inactive'><a  class='paginate' id='" + pages + "'>" + pages + "</a></li>";
    }

    if (pages > 5 && pageNumber >= 5 && pageNumber == pages - 2) {

        if (pageNumber == pageNumber - 3) {
            pagination += "<li class='active'><a  class='paginate' id='" + (pageNumber - 3) + "'>" + (pageNumber - 3) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + ( pageNumber - 3) + "'>" + (pageNumber - 3) + "</a></li>";
        }

        if (pageNumber == pageNumber - 2) {
            pagination += "<li class='active'><a class='paginate'  id='" + (pageNumber - 2) + "'>" + (pageNumber - 2) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + ( pageNumber - 2) + "'>" + (pageNumber - 2) + "</a></li>";
        }

        if (pageNumber == pageNumber - 1) {
            pagination += "<li class='active'><a  class='paginate' id='" + (pageNumber - 1) + "'>" + (pageNumber - 1) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + (pageNumber - 1) + "'>" + (pageNumber - 1) + "</a></li>";
        }
        if (pageNumber == pageNumber) {
            pagination += "<li class='active'><a  class='paginate' id='" + pageNumber + "'>" + pageNumber + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + pageNumber + "'>" + pageNumber + "</a></li>";
        }
        if (pageNumber == pageNumber + 1) {
            pagination += "<li class='active'><a class='paginate'  id='" + (pageNumber + 1) + "'>" + (pageNumber + 1) + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a class='paginate'  id='" + (pageNumber + 1) + "'>" + (pageNumber + 1) + "</a></li>";
        }

        pagination += "<li class='inactive'><a>...</a></li>";
        pagination += "<li class='inactive'><a  class='paginate' id='" + pages + "'>" + pages + "</a></li>";
    }

    if (pages > 5 && pageNumber >= 5 && pageNumber > pages - 2 && pageNumber != pages) {

        for (iterator = 4; iterator >= 1; iterator--) {

            if (pageNumber == pageNumber - iterator) {
                pagination += "<li class='active'><a  class='paginate' id='" + (pageNumber - iterator) + "'>" + (pageNumber - iterator) + "</a></li>";
            }
            else {
                pagination += "<li class='inactive'><a class='paginate'  id='" + (pageNumber - iterator) + "'>" + (pageNumber - iterator) + "</a></li>";
            }
        }
        if (pageNumber == pageNumber) {
            pagination += "<li class='active'><a  class='paginate' id='" + pageNumber + "'>" + pageNumber + "</a></li>";
        }
        else {
            pagination += "<li class='inactive'><a  class='paginate' id='" + pageNumber + "'>" + pageNumber + "</a></li>";
        }

        pagination += "<li class='inactive'><a>...</a></li>";
        pagination += "<li class='inactive'><a  class='paginate' id='" + pages + "'>" + pages + "</a></li>";
    }

    $('.pagination').html(pagination);
}