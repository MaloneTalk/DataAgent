# Engineering Principles

**Keep code simple: avoid overly long files and functions to manage complexity.** When a file exceeds ~400 lines or a function exceeds ~80 lines, consider splitting it. Break complex logic into small, focused units to reduce cognitive load.

**YAGNI — don't add code or abstractions until it's actually needed.** If a feature, interface, or parameter isn't required by the current requirement, omit it. You Aren't Gonna Need It.

**Prefer existing solutions before writing new code**, in this order:
1. JDK standard library (java.util, java.time, etc.)
2. Platform-native features (Spring IoC, AOP, validation, etc.)
3. Already-installed dependencies (check pom.xml first)
4. Only then: write custom code

**Use chaining and fluent style where natural, but don't force one-liners.** Break long expressions into multiple lines if that improves readability — especially when dealing with Optional chains, complex ternaries, or multi-step transformations.

**Avoid over-encapsulation: don't abstract before duplication appears.** A helper method used only once is just indirection, not cleanliness. Extract when the same pattern appears in ≥2 places — not earlier. **But one-off private methods that clarify intent are not "duplication" — they're organization.**

**Don't write boilerplate that can be generated.** Use Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`), MapStruct for mappers, and IDE generation for equals/hashCode. If it's mechanical, don't type it by hand. **For records with many fields, prefer `@Builder` over positional `new` — the named setter style improves readability at every call site.** A 6-argument `new Xxx(a, b, c, d, e, f)` forces readers to count positions; `.name(a).type(b)...` does not.

**Prefer imports over fully-qualified names for JDK and framework classes.** Import the class at the top and use its short name in code — `Objects::nonNull`, not `java.util.Objects::nonNull`. The exception is when a project class shares the same simple name as a JDK or framework class you need to use. In that case, qualify the JDK/framework class with its full package path everywhere — even if the conflicting project class isn't imported in the current file. For example, use `javax.sql.DataSource` fully-qualified because `io.github.malonetalk.entity.Datasource` exists in the project and the two names are easily confused.

**Write code for the next reader, not for the compiler.** The compiler can parse anything. A human shouldn't have to. Choose names that reveal intent, structure code in small logical steps, and prefer clarity over cleverness. If a line makes you pause — it will make someone else pause too.
