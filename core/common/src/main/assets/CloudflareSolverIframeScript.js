(function __SOLVER__() {
  addEventListener("message", function __SOLVER__(e) {
    console.log(`${location.origin}: ${JSON.stringify(e.data)}`);
  });

  const stackFilter = function __SOLVER__(site) {
    return !site.toString().includes("__SOLVER__");
  };

  const objectToProxy = new WeakMap();
  const proxyToObject = new WeakMap();

  const createProxy = function __SOLVER__(target, handler) {
    if (objectToProxy.has(target)) {
      return objectToProxy.get(target);
    }
    const proxy = new Proxy(target, handler);
    objectToProxy.set(target, proxy);
    proxyToObject.set(proxy, target);
    return proxy;
  };

  const toProxy = function __SOLVER__(obj) {
    return objectToProxy.has(obj) ? objectToProxy.get(obj) : obj;
  };

  const toObject = function __SOLVER__(proxy) {
    return proxyToObject.has(proxy) ? proxyToObject.get(proxy) : proxy;
  };

  const redirectFunctionHandler = {
    apply: function __SOLVER__(target, thisArg, args) {
      return Reflect.apply(toObject(target), toObject(thisArg), args.map(toObject));
    }
  };

  const getRedirectPropertyHandler = function __SOLVER__(redirects) {
    return {
      defineProperty: function __SOLVER__(target, prop, descriptor) {
        return Reflect.defineProperty(redirects[prop] ?? target, prop, descriptor);
      },
      deleteProperty: function __SOLVER__(target, prop) {
        return Reflect.deleteProperty(redirects[prop] ?? target, prop);
      },
      get: function __SOLVER__(target, prop, receiver) {
        return toProxy(Reflect.get(redirects[prop] ?? target, prop, toObject(receiver)));
      },
      getOwnPropertyDescriptor: function __SOLVER__(target, prop) {
        return Reflect.getOwnPropertyDescriptor(redirects[prop] ?? target, prop);
      },
      has: function __SOLVER__(target, prop, receiver) {
        return Reflect.has(redirects[prop] ?? target, prop, toObject(receiver));
      },
      ownKeys: function __SOLVER__(target) {
        const result = Reflect.ownKeys(target).filter(function __SOLVER__(key) {
          return !(key in redirects);
        });
        for (const prop in redirects) {
          if (prop in redirects[prop]) {
            result.push(prop);
          }
        }
        return result;
      },
      set: function __SOLVER__(target, prop, value) {
        return Reflect.set(redirects[prop] ?? target, prop, toObject(value));
      }
    };
  };

  let CallSite;
  Error.prepareStackTrace = function __SOLVER__(_, sites) {
    CallSite = sites[0].constructor;
  };
  new Error().stack;
  delete Error.prepareStackTrace;

  if (CallSite) {
    // V8, i.e. Chrome

    const prepareStackTrace = function __SOLVER__(error, callSites) {
      return callSites.reduce(
        function __SOLVER__(acc, cur) {
          return acc + "\n    at " + cur;
        },
        `${error.name}: ${error.message}`
      );
    };

    const prepareStackTraceObj = {};
    Error.prepareStackTrace = function __SOLVER__(error, callSites) {
      return (
        prepareStackTraceObj.prepareStackTrace ?? prepareStackTrace
      )(error, callSites.filter(stackFilter));
    };
    window.Error = Error.prototype.constructor = createProxy(Error, getRedirectPropertyHandler({
      prepareStackTrace: prepareStackTraceObj
    }));
  } else if (Object.hasOwn(Error.prototype, "stack")) {
    // Gecko, i.e. FireFox

    const stackDescriptor = Object.getOwnPropertyDescriptor(Error.prototype, "stack");
    const stacks = WeakMap();

    Object.defineProperty(Error.prototype, "stack", Object.assign({}, stackDescriptor, {
      get: createProxy(stackDescriptor.get, {
        apply: function __SOLVER__(target, thisArg) {
          thisArg = toObject(thisArg);
          if (stacks.has(thisArg)) {
            return stacks.get(thisArg);
          } else {
            const value = target.call(thisArg).split('\n').filter(stackFilter).join('\n');
            stacks.set(thisArg, value);
            return value;
          }
        }
      }),
      set: createProxy(stackDescriptor.set, {
        apply: function __SOLVER__(target, thisArg, [value]) {
          stacks.set(toObject(thisArg), toObject(value));
          return true;
        }
      })
    }));
  } else {
    // Others, i.e. Safari

    const proxyErrorHandler = {
      apply: function __SOLVER__(target, thisArg, args) {
        const result = Reflect.apply(target, toObject(thisArg), args.map(toObject));
        result.stack = result.stack.split('\n').filter(stackFilter).join('\n');
        return result;
      },
      construct: function __SOLVER__(target, args) {
        const result = Reflect.construct(target, args.map(toObject));
        result.stack = result.stack.split('\n').filter(stackFilter).join('\n');
        return result;
      }
    };

    for (const prop of Object.getOwnPropertyNames(window)) {
      try {
        if (window[prop] === Error || window[prop]?.prototype instanceof Error) {
          const proxy = createProxy(window[prop], proxyErrorHandler);
          Object.defineProperty(window[prop].prototype, "constructor", {value: proxy});
          Object.defineProperty(window, prop, {value: proxy});
        }
      } catch {}
    }
  }

  const fixIllegalInvocation = function __SOLVER__(obj) {
    try {
      while (obj && obj !== Object.prototype) {
        const descriptors = Object.getOwnPropertyDescriptors(obj);
        for (const prop of Object.getOwnPropertyNames(descriptors).concat(Object.getOwnPropertySymbols(descriptors))) {
          if (prop === "constructor") {
            continue;
          }
          const descriptor = descriptors[prop];
          if (!descriptor.configurable) {
            continue;
          }
          if (descriptor.get) {
            descriptor.get = createProxy(descriptor.get, redirectFunctionHandler);
          }
          if (descriptor.set) {
            descriptor.set = createProxy(descriptor.set, redirectFunctionHandler);
          }
          if (typeof descriptor.value === "function") {
            descriptor.value = createProxy(descriptor.value, redirectFunctionHandler);
          }
          try {
            Object.definePropery(obj, prop, descriptor);
          } catch {}
        }
        obj = Object.getPrototypeOf(obj);
      }
    } catch {}
  };

  const shadows = new WeakMap();

  const getCheckBox = function __SOLVER__() {
    return shadows.get(document.body)?.querySelector('input[type="checkbox"]');
  };

  Element.prototype.attachShadow = createProxy(Element.prototype.attachShadow, {
    apply: function __SOLVER__(target, thisArg, args) {
      thisArg = toObject(thisArg);
      const result = target.apply(thisArg, args.map(toObject));
      shadows.set(thisArg, result);
      return result;
    }
  });

  const isTrustedPropertyDescriptor = Object.getOwnPropertyDescriptor(new Event(""), "isTrusted");
  const isTrustedObj = {};

  Object.defineProperty(isTrustedObj, "isTrusted", Object.assign(isTrustedPropertyDescriptor, {
    get: createProxy(isTrustedPropertyDescriptor.get, {
      apply: function __SOLVER__() {
        return true;
      }
    }),
  }));

  const proxyEventHandler = getRedirectPropertyHandler({ isTrusted: isTrustedObj });

  const patchedEvents = new WeakMap();
  const original = new WeakMap();
  const modified = new WeakMap();

  Object.assign(EventTarget.prototype, {
    addEventListener: createProxy(EventTarget.prototype.addEventListener, {
      apply: function __SOLVER__(target, thisArg, args) {
        thisArg = toObject(thisArg);
        args = args.map(toObject)
        const [type, listener, options] = args;
        if (listener instanceof Object) {
          if (!modified.has(listener)) {
            const newListener = typeof listener === "function" ? function __SOLVER__(e) {
              return listener.call(this, patchedEvents.has(e) ? patchedEvents.get(e) : e);
            } : function __SOLVER__(e) {
              return listener.handleEvent(patchedEvents.has(e) ? patchedEvents.get(e) : e);
            };
            modified.set(listener, newListener);
            original.set(newListener, listener);
          }
          args[1] = modified.get(listener);
        }
        return Reflect.apply(target, thisArg, args);
      }
    }),
    removeEventListener: createProxy(EventTarget.prototype.removeEventListener, {
      apply: function __SOLVER__(target, thisArg, args) {
        thisArg = toObject(thisArg);
        args = args.map(toObject);
        const [type, listener, options] = args;
        if (listener instanceof Object) {
          args[1] = original.get(listener) ?? listener;
        }
        return Reflect.apply(target, thisArg, args);
      }
    })
  });

  const eventDescriptor = Object.getOwnPropertyDescriptor(window, "event");

  Object.defineProperty(window, "event", Object.assign(eventDescriptor, {
    get: createProxy(eventDescriptor.get, {
      apply: function __SOLVER__(target, thisArg) {
        return toProxy(target.call(thisArg));
      }
    }),
    set: createProxy(eventDescriptor.set, {
      apply: function __SOLVER__(target, thisArg, [value]) {
        return target.call(thisArg, toObject(value));
      }
    })
  }));

  const simulateMouseClick = async function __SOLVER__(element, clientX = null, clientY = null) {
    if (clientX === null || clientY === null) {
      const box = element.getBoundingClientRect();
      clientX = box.left + box.width / 2;
      clientY = box.top + box.height / 2;
    }

    if (isNaN(clientX) || isNaN(clientY)) {
      return;
    }

    // Send mouseover, mousedown, mouseup, click, mouseout
    for (const eventName of [
      "mouseover",
      "mouseenter",
      "mousedown",
      "mouseup",
      "click",
      "mouseout"
    ]) {
      const event = new MouseEvent(eventName, {
        detail: 1 - (eventName === "mouseover"),
        bubbles: true,
        cancelable: true,
        clientX: clientX,
        clientY: clientY,
      });
      patchedEvents.set(event, createProxy(event, proxyEventHandler));
      element.dispatchEvent(event);
      await new Promise(function __SOLVER__(resolve) {
        setTimeout(resolve, 10);
      });
    }
  }

  fixIllegalInvocation(MouseEvent.prototype);

  setInterval(function __SOLVER__() {
    const checkbox = getCheckBox();
    if (checkbox) {
      simulateMouseClick(checkbox);
    }
  }, 100);
})();
