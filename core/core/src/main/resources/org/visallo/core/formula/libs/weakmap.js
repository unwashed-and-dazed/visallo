// https://gist.github.com/Gozala/1269991
/* vim:set ts=2 sw=2 sts=2 expandtab */
/*jshint asi: true undef: true es5: true node: true devel: true
         forin: false latedef: false */
/*global define: true */

if (typeof(WeakMap) === 'undefined') WeakMap = (function(global) {
  "use strict";

  function defineNamespace(object, namespace) {
    /**
    Utility function takes `object` and `namespace` and overrides `valueOf`
    method of `object`, so that when called with a `namespace` argument,
    `private` object associated with this `namespace` is returned. If argument
    is different, `valueOf` falls back to original `valueOf` property.
    **/

    // Private inherits from `object`, so that `this.foo` will refer to the
    // `object.foo`. Also, original `valueOf` is saved in order to be able to
    // delegate to it when necessary.
    var privates = Object.create(object), base = object.valueOf
    Object.defineProperty(object, 'valueOf', { value: function valueOf(value) {
      // If `this` or `namespace` is not associated with a `privates` being
      // stored we fallback to original `valueOf`, otherwise we return privates.
      return value != namespace || this != object ? base.apply(this, arguments)
                                                  : privates
    }, configurable: true })
    return privates
  }

  function Name() {
    /**
    Desugared implementation of private names proposal. API is different as
    it's not possible to implement API proposed for harmony with in ES5. In
    terms of provided functionality it supposed to be same.
    http://wiki.ecmascript.org/doku.php?id=strawman:private_names
    **/

    var namespace = {}
    return function name(object) {
      var privates = object.valueOf(namespace)
      return privates !== object ? privates : defineNamespace(object, namespace)
    }
  }

  function guard(key) {
    /**
    Utility function to guard WeakMap methods from keys that are not
    a non-null objects.
    **/

    if (key !== Object(key)) throw TypeError("value is not a non-null object")
    return key
  }

  function WeakMap() {
    /**
    Implementation of harmony `WeakMaps`, in ES5. This implementation will
    work only with keys that have configurable `valueOf` property (which is
    a default for all non-frozen objects).
    http://wiki.ecmascript.org/doku.php?id=harmony:weak_maps
    **/

    var privates = Name()

    return Object.freeze(Object.create(WeakMap.prototype, {
      has: {
        value: function has(object) {
          return 'value' in privates(object)
        },
        configurable: true,
        enumerable: false,
        writable: true
      },
      get: {
        value: function get(key, fallback) {
          return privates(guard(key)).value || fallback
        },
        configurable: true,
        enumerable: false,
        writable: true
      },
      set: {
        value: function set(key, value) {
          privates(guard(key)).value = value
        },
        configurable: true,
        enumerable: false,
        writable: true
      },
      'delete': {
        value: function set(key) {
          return delete privates(guard(key)).value
        },
        configurable: true,
        enumerable: false,
        writable: true
      }
    }))
  }

  return global.WeakMap = WeakMap
})(this)