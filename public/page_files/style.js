var currPercent = 0;

String.prototype.repeat = function( num ) {
    return new Array( num + 1 ).join( this );
};

function shuffle(o) {
    for(var j, x, i = o.length; i; j = parseInt(Math.random() * i), x = o[--i], o[i] = o[j], o[j] = x);
    return o;
}

function popDots(percent) {
    if (!$('#map').is(':visible')) return;
    var speed   = {"asia": [], "europe": [], "africa": [], "australia": [], "north-america": [], "south-america": [], "greenland": []};
    var names   = ["asia", "europe", "africa", "australia", "north-america", "south-america", "greenland"];
    $(names).each(function() {
        var name    = this;
        var $es     = $('#map .dot.' + name);
        $es.each(function(j) {
            if (1 === percent) {
                val = 1;
            } else {
                val = (j / ($es.length - 1)) * (1 / (1 - percent));
                if (val > 1) {
                    val = 1;
                } else {
                    val = Math.pow(val, dotTension);
                }
            }
            speed[name].push(val);
        });
        speed[name] = shuffle(speed[name]);
        $es.each(function(j) {
            var $dot    = $(this);
            setTimeout(function() {
                $dot.css('transform', 'scale(' + speed[name][j] + ')');
                //$dot.css('transform', 'scale(0.5)').css('background', '#3b3b3b');
            }, Math.random() * dotDelay);
        });
    });
}

function setCircle(type, value, max, first) {
    var $both   = $('#circle .' + type + ' .left > img, #circle .' + type + ' .right > img');
    var $left   = $('#circle .' + type + ' .left > img');
    var $right  = $('#circle .' + type + ' .right > img');
    var $value  = $('#circle .' + type + ' .value');
    var $val    = $('#circle .' + type + ' .val');
    
    $value.html('<span class="number">' + value + '</span>');
    if ('days' === type || 'hours' === type) {
        $value.prepend(1 === value ? $value.attr('data-name') : $value.attr('data-plural-name'));
    } else {
        $value.append(1 === value ? $value.attr('data-name') : $value.attr('data-plural-name'));
    }
    if (0 === value) {
        $value.toggleClass('roll');
    }
    
    $val.html('');
    var str = value.toString();
    if ((str.length <= 1 && 'days' !== type) || (str.length <= 2 && 'days' === type)) {
        str = "0".repeat('days' === type ? 3 - str.length : 2 - str.length) + str;
    }
    for(var i = 0; i < str.length; i++) {
        $val.append('<div class="number ' + type + '-' + (str.substring(i, i + 1)) + '"><img src="./img/spacer.gif" alt="" /></div>');
    }
    
    var rotate  = function() {
        var angle = value / max;
        $left.css('transform', 'rotate(' + -Math.min(360 - 360 * angle, 180) + 'deg)');
        $right.css('transform', 'rotate(' + (Math.min(360 * angle, 180) - 180) + 'deg)');
    };
    
    if (first) {
        $both.addClass('reset');
    } else {
        $both.removeClass('reset');
    }
    
    if (0 === value) {
        $both.css('transform', 'rotate(-180deg)');
        setTimeout(function() {
            $both.addClass('reset');
        }, 990);
        return;
    }
    
    if (max - 1 === value) {
        $both.addClass('reset');
        $both.css('transform', 'rotate(0deg)');
        setTimeout(function() {
            if (!first) {
                $both.removeClass('reset');
            }
            rotate();
        }, 10);
        return;
    }
    
    rotate();
}

function setSeconds(seconds, first) {
    setCircle('seconds', seconds, 60, first);
}

function setMinutes(minutes, first) {
    setCircle('minutes', minutes, 60, first);
}

function setHours(hours, first) {
    setCircle('hours', hours, 24, first);
}

function setDays(days, max, first) {
    setCircle('days', days, max, first);
}

function setPercent(percent, first) {
    currPercent = percent;
    var major   = Math.floor(percent * 100);
    var minor   = Math.floor(percent * 10000) % 100;
    $('#circle .percent').attr('class', 'percent size-' + major.toString().length);
    $('#circle .percent .major').html(major);
    $('#circle .percent .minor').html(1 === minor.toString().length ? '0' + minor : minor);
    if (first) {
        setTimeout(function() {
            popDots(percent);
        }, 1500);
    } else {
        popDots(percent);
    }
}

var cyclePercent, cycleDays, cycleHours, cycleMinutes, cycleSeconds;
function cycleCircle(first) {
    var start           = new Date(startDate);
    var end             = new Date(endDate);
    var now             = new Date();
    var startTimestamp  = Math.ceil(start.getTime() / 1000);
    var endTimestamp    = Math.ceil(end.getTime() / 1000);
    var nowTimestamp    = Math.ceil(now.getTime() / 1000);
    var difference      = Math.max(endTimestamp - nowTimestamp, 0);
    var percent         = Math.round((1 - difference / (endTimestamp - startTimestamp)) * 10000) / 10000;
    var days            = Math.floor(difference / 86400);
    var hours           = Math.floor((difference - days * 86400) / 3600);
    var minutes         = Math.floor((difference - days * 86400 - hours * 3600) / 60);
    var seconds         = difference - days * 86400 - hours * 3600 - minutes * 60;
    if (cycleSeconds !== seconds) {
        cycleSeconds    = seconds;
        setSeconds(seconds, first);
    }
    if (cycleMinutes !== minutes) {
        cycleMinutes    = minutes;
        setMinutes(minutes, first);
    }
    if (cycleHours !== hours) {
        cycleHours      = hours;
        setHours(hours, first);
    }
    if (cycleDays !== days) {
        cycleDays       = days;
        setDays(days, Math.floor((endTimestamp - startTimestamp) / 86400), first);
    }
    if (cyclePercent !== percent) {
        cyclePercent    = percent;
        setPercent(percent, first);
    }
    setTimeout(cycleCircle, timeRefresh);
}

function resizeUI() {
    var width   = $(window).width();
    var ltWidth = $('.line .title').eq(0).width();
    $('.line').css('background-position', (((width - ltWidth) / 2) % ltWidth) + 'px 0px');
}

function applyMargins() {
    var winHeight   = $(window).height();
    var winScroll   = $(window).scrollTop();
    var current     = -1;
    $('.margin-group').each(function() {
        var $group  = $(this);
        var scroll  = Math.min(Math.max($group.offset().top - winScroll, 0), winHeight) / winHeight;
        $group.find('.margin-element').each(function() {
            var $element    = $(this);
            var initMargin  = $element.data('initMargin');
            if ("undefined" === typeof initMargin) {
                $element.data('initMargin', initMargin = parseInt($element.css('margin-top')));
            }
            $element.css({'margin-top': initMargin + marginSpan * scroll});
        });
    }).each(function(index) {
        if ($(this).offset().top >= winScroll && $(this).offset().top <= winScroll + winHeight) {
            current = index;
            return false;
        }
    });
    $('.up').unbind('click').bind('click', function(e) {
        e.preventDefault();
        $(window).scrollTo(0 === current ? {top: 0, left: 0} : $('.margin-group').eq(current - 1), 1000);
    });
    $('.down').unbind('click').bind('click', function(e) {
        e.preventDefault();
        $(window).scrollTo($('.margin-group').eq(Math.min(current + 1, $('.margin-group').length)), 1000);
    });
    $('.up, .down').show();
    if (0 === current) {
        $('.up').hide();
    }
    if ($('.margin-group').length <= current + 1) {
        $('.down').hide();
    }
}

$(document).ready(function() {
    resizeUI();
    applyMargins();
});

$(window).load(function() {
    $('#circle .days, #circle .hours, #circle .minutes, #circle .seconds').addClass('visible');
    cycleCircle(true);
});

var resizeTimeout;
$(window).resize(function() {
    clearTimeout(resizeTimeout);
    resizeTimeout = setTimeout(function() {
        resizeUI();
        applyMargins();
        popDots(currPercent);
    }, 350);
}).scroll(function() {
    applyMargins();
});