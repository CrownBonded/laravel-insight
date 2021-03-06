package net.rentalhost.idea.laravelInsight.blade;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.blade.psi.BladeFileImpl;
import com.jetbrains.php.blade.psi.BladePsiDirective;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BladeFoldingBuilder extends FoldingBuilderEx {
    private static final int     MULTILINE_COMMENT_LENGTH_LIMIT = 32;
    private static final Pattern MULTIPLE_SPACES                = Pattern.compile("\\s+\\s");

    private static void processDirectives(
        @Nullable final BladePsiDirective baseDirective,
        @NotNull final Queue<BladePsiDirective> directives,
        @NotNull final Collection<FoldingDescriptor> foldingDescriptors,
        @NotNull final Document document
    ) {
        while (true) {
            final BladePsiDirective directive = directives.peek();

            if (directive == null) {
                break;
            }

            if (baseDirective == null) {
                if (!directive.isToBeClosed()) {
                    directives.poll();
                    continue;
                }

                processDirectives(directives.poll(), directives, foldingDescriptors, document);
                continue;
            }

            // Eg. @endif or @elseif closes @if.
            // Or that @elseif continues @if.
            if (directive.closes(baseDirective) ||
                directive.continues(baseDirective)) {
                // Eg. @endif closes definitively an @if, @else or @elseif.
                // But @elseif or @else don't have the same effect.
                final boolean isDefinitivelyClosing = directive.isClosing() &&
                                                      !directive.isContinued();

                final TextRange foldingRange = new TextRange(
                    baseDirective.getTextRange().getEndOffset(),
                    directive.getTextRange().getStartOffset() - calculateEndOffsetReductor(directive, isDefinitivelyClosing)
                );

                if ((foldingRange.getLength() > 0) &&
                    !StringUtils.strip(document.getText(foldingRange), " ").isEmpty()) {
                    foldingDescriptors.add(new FoldingDescriptor(baseDirective.getNode(), foldingRange, FoldingGroup.newGroup("Blade")));
                }

                if (isDefinitivelyClosing) {
                    directives.poll();
                    break;
                }

                processDirectives(directives.poll(), directives, foldingDescriptors, document);
                break;
            }

            // Eg. @if or @elseif (but it will be catched on previous condition).
            if (directive.isContinued()) {
                processDirectives(directives.poll(), directives, foldingDescriptors, document);
                continue;
            }

            directives.poll();
        }
    }

    private static int calculateEndOffsetReductor(
        @NotNull final PsiElement directive,
        final boolean isDefinitivelyClosing
    ) {
        if (!isDefinitivelyClosing) {
            final PsiElement directiveWhitespace = directive.getPrevSibling();

            if (directiveWhitespace instanceof LeafPsiElement) {
                final String directiveWhitespaceText = directiveWhitespace.getText();
                final int    lastBreakline           = directiveWhitespaceText.lastIndexOf('\n');

                if (lastBreakline != -1) {
                    return directiveWhitespaceText.length() - lastBreakline;
                }
            }
        }

        return 0;
    }

    private static void processComments(
        final Iterable<PsiComment> comments,
        final Collection<FoldingDescriptor> foldingDescriptors
    ) {
        for (final PsiComment comment : comments) {
            final String commentContentsRaw = comment.getText();
            final String commentContents    = commentContentsRaw.substring(4, commentContentsRaw.length() - 4);

            // Applicable only for multiline comments.
            if (commentContents.indexOf('\n') != -1) {
                final TextRange commentTextRange = comment.getTextRange();
                final TextRange commentRange     = new TextRange(commentTextRange.getStartOffset(), commentTextRange.getEndOffset());

                String commentContentsParsed = MULTIPLE_SPACES.matcher(commentContents.trim()).replaceAll(" ");

                if (commentContentsParsed.length() >= MULTILINE_COMMENT_LENGTH_LIMIT) {
                    commentContentsParsed = commentContentsParsed.substring(0, MULTILINE_COMMENT_LENGTH_LIMIT - 3) + "...";
                }

                final String finalCommentContentsParsed = commentContentsParsed;

                foldingDescriptors.add(new FoldingDescriptor(comment.getNode(), commentRange, FoldingGroup.newGroup("Blade")) {
                    @Override
                    public String getPlaceholderText() {
                        return "{{-- " + finalCommentContentsParsed + " --}}";
                    }
                });
            }
        }
    }

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(
        @NotNull final PsiElement root,
        @NotNull final Document document,
        final boolean quick
    ) {
        if (!(root instanceof BladeFileImpl)) {
            return FoldingDescriptor.EMPTY;
        }

        final Queue<BladePsiDirective> directives         = new LinkedBlockingQueue(Arrays.asList(((BladeFileImpl) root).getDirectives()));
        final List<FoldingDescriptor>  foldingDescriptors = new ArrayList<>();

        processDirectives(null, directives, foldingDescriptors, document);
        processComments(PsiTreeUtil.findChildrenOfType(root, PsiComment.class), foldingDescriptors);

        return foldingDescriptors.toArray(new FoldingDescriptor[foldingDescriptors.size()]);
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull final ASTNode node) {
        return false;
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull final ASTNode node) {
        return " {...} ";
    }
}
