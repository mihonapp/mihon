use adblock::{
    Engine,
    lists::{FilterSet, ParseOptions, RuleTypes, parse_filter},
    request::Request,
};
use jni::{
    JNIEnv,
    objects::{JClass, JString},
    sys::{JNI_FALSE, jboolean},
};
use std::{
    panic::{AssertUnwindSafe, catch_unwind},
    sync::RwLock,
};

static ENGINE: RwLock<Option<Engine>> = RwLock::new(None);

fn network_options() -> ParseOptions {
    ParseOptions {
        rule_types: RuleTypes::NetworkOnly,
        ..ParseOptions::default()
    }
}

fn compile(rules: String) -> Engine {
    let mut lists = FilterSet::new(false);
    lists.add_filter_list(rules, network_options());
    Engine::new_with_filter_set(lists)
}

fn validate(rules: &str, strict: bool) -> bool {
    let mut rules = rules.lines().filter(|line| !line.trim().is_empty());
    let is_valid = |line: &str| parse_filter(line, false, network_options()).is_ok();
    if strict {
        rules.all(is_valid)
    } else {
        rules.any(is_valid)
    }
}

fn jni_boolean(action: impl FnOnce() -> bool) -> jboolean {
    catch_unwind(AssertUnwindSafe(action)).map_or(JNI_FALSE, jboolean::from)
}

fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

// SAFETY: The symbol and arguments match the JVM static method. JNI references stay in this call.
#[allow(unsafe_code, clippy::needless_pass_by_value)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_app_komikita_core_nativecore_NativeAdBlockEngine_nativeReplaceRules(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    rules: JString<'_>,
) -> jboolean {
    jni_boolean(|| {
        let Some(rules) = read_string(&mut env, &rules) else {
            return false;
        };
        let engine = (!rules.is_empty()).then(|| compile(rules));
        let Ok(mut stored) = ENGINE.write() else {
            return false;
        };
        *stored = engine;
        true
    })
}

// SAFETY: The symbol and arguments match the JVM static method. JNI references stay in this call.
#[allow(unsafe_code, clippy::needless_pass_by_value)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_app_komikita_core_nativecore_NativeAdBlockEngine_nativeValidateRules(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    rules: JString<'_>,
    strict: jboolean,
) -> jboolean {
    jni_boolean(|| {
        read_string(&mut env, &rules).is_some_and(|rules| validate(&rules, strict != JNI_FALSE))
    })
}

// SAFETY: The symbol and arguments match the JVM static method. JNI references stay in this call.
#[allow(unsafe_code, clippy::needless_pass_by_value)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_app_komikita_core_nativecore_NativeAdBlockEngine_nativeShouldBlock(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    url: JString<'_>,
    source_url: JString<'_>,
    resource_type: JString<'_>,
    method: JString<'_>,
) -> jboolean {
    jni_boolean(|| {
        let (Some(url), Some(source_url), Some(resource_type), Some(method)) = (
            read_string(&mut env, &url),
            read_string(&mut env, &source_url),
            read_string(&mut env, &resource_type),
            read_string(&mut env, &method),
        ) else {
            return false;
        };
        let Ok(request) = Request::new(&url, &source_url, &resource_type, &method) else {
            return false;
        };
        ENGINE.read().is_ok_and(|engine| {
            engine
                .as_ref()
                .is_some_and(|engine| engine.check_network_request(&request).should_block())
        })
    })
}

#[cfg(test)]
mod tests {
    use super::{compile, validate};
    use adblock::request::Request;

    #[test]
    fn matches_paths_resource_types_party_and_exceptions() {
        let engine = compile(
            "||cdn.example.org/ads/*$script,third-party\n@@||cdn.example.org/ads/preview.js$script"
                .to_owned(),
        );
        let blocks = |path: &str, origin: &str, resource_type: &str| {
            let url = format!("https://cdn.example.org{path}");
            let request = Request::new(&url, origin, resource_type, "GET").unwrap();
            engine.check_network_request(&request).should_block()
        };
        let manga = "https://manga.example.net/title";

        assert!(blocks("/ads/banner.js", manga, "script"));
        assert!(!blocks("/ads/banner.js", manga, "image"));
        assert!(!blocks("/assets/banner.js", manga, "script"));
        assert!(!blocks(
            "/ads/banner.js",
            "https://reader.example.org/",
            "script"
        ));
        assert!(!blocks("/ads/preview.js", manga, "script"));
    }

    #[test]
    fn validates_only_network_rules() {
        let supported = "||cdn.example.org/ads/*$script,third-party";
        let mixed = format!("{supported}\nmanga.example.org##.banner");
        assert!(validate(supported, true));
        assert!(validate(&mixed, false));
        assert!(!validate(&mixed, true));
        assert!(!validate("manga.example.org##.banner", false));
        assert!(validate("", true));
    }
}
