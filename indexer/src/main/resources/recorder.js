(function () {
  if (window.__recorderInstalled) return;
  window.__recorderInstalled = true;

  var CLICKABLE = 'button, a, input, select, textarea, [role="button"]';
  var KEY = '__recorderEvents';

  function push(event) {
    var list = JSON.parse(sessionStorage.getItem(KEY) || '[]');
    list.push(event);
    sessionStorage.setItem(KEY, JSON.stringify(list));
  }

  function count(selector) {
    try { return document.querySelectorAll(selector).length; } catch (e) { return 0; }
  }

  function countXpath(xpath) {
    try {
      return document.evaluate('count(' + xpath + ')', document, null,
          XPathResult.NUMBER_TYPE, null).numberValue;
    } catch (e) { return 0; }
  }

  function quote(value) {
    return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  }

  function describe(el) {
    var tag = el.tagName.toLowerCase();
    var type = el.getAttribute('type') || '';
    var isButtonInput = el.tagName === 'INPUT' && (type === 'submit' || type === 'button');
    var label = el.innerText || (isButtonInput ? el.value : '') || '';
    var text = label.replace(/\s+/g, ' ').trim().slice(0, 60);

    var testIdAttr = el.hasAttribute('data-testid') ? 'data-testid'
                   : el.hasAttribute('data-test') ? 'data-test' : '';
    var testId = testIdAttr ? el.getAttribute(testIdAttr) : '';
    var id = el.id || '';
    var name = el.getAttribute('name') || '';

    var testIdCount = testId ? count('[' + testIdAttr + '="' + quote(testId) + '"]') : 0;
    var idCount = id ? count('[id="' + quote(id) + '"]') : 0;
    var nameCount = name ? count('[name="' + quote(name) + '"]') : 0;

    var textCount = 0;
    if ((tag === 'button' || tag === 'a') && text && text.length < 60 && text.indexOf("'") === -1) {
      textCount = countXpath('//' + tag + "[normalize-space()='" + text + "']");
    }

    return {
      tag: tag, id: id, name: name, type: type, text: text,
      testIdAttr: testIdAttr, testId: testId,
      idCount: idCount, testIdCount: testIdCount, nameCount: nameCount, textCount: textCount
    };
  }

  // For an expectation, the clicked thing is often plain text (a title, a message).
  // Climb up a few levels to the nearest element that has a test attribute or an id.
  function pickExpectTarget(el) {
    var current = el;
    for (var i = 0; i < 4 && current; i++) {
      if (current.hasAttribute('data-testid') || current.hasAttribute('data-test') || current.id) {
        return current;
      }
      current = current.parentElement;
    }
    return el;
  }

  document.addEventListener('click', function (e) {

    // ALT + click = "I expect to see this". Record it and stop the page from reacting.
    if (e.altKey) {
      e.preventDefault();
      e.stopPropagation();
      var target = pickExpectTarget(e.target);
      var expectation = describe(target);
      expectation.kind = 'expect';
      // full text, not cut at 60 characters like the short label
      expectation.value = (target.innerText || '').replace(/\s+/g, ' ').trim();
      push(expectation);

      // flash green so the tester sees it was recorded
      var oldOutline = target.style.outline;
      target.style.outline = '3px solid #2e7d32';
      setTimeout(function () { target.style.outline = oldOutline; }, 700);
      return;
    }

    var el = e.target.closest(CLICKABLE);
    if (!el) return;

    var t = (el.getAttribute('type') || 'text').toLowerCase();
    var isTextField = el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' ||
        (el.tagName === 'INPUT' &&
         ['submit', 'button', 'checkbox', 'radio', 'reset', 'image'].indexOf(t) === -1);
    if (isTextField) return;

    var event = describe(el);
    event.kind = 'click';
    event.value = '';
    push(event);
  }, true);

  document.addEventListener('change', function (e) {
    var el = e.target;
    if (!el || !el.tagName) return;
    var event = describe(el);
    event.kind = 'input';
    event.value = (el.type === 'password') ? '<masked>' : (el.value || '');
    push(event);
  }, true);
})();